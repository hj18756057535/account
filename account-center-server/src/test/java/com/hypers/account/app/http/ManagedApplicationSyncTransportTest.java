package com.hypers.account.app.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.ApplicationSyncDelivery;
import com.hypers.account.app.ApplicationSyncFailure;
import com.hypers.account.app.ApplicationSyncPolicy;
import com.hypers.account.config.ApplicationSyncProperties;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.security.HmacSignatureService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.env.MockEnvironment;

class ManagedApplicationSyncTransportTest {
    private HttpServer server;
    private ApplicationSyncProperties properties;
    private MockEnvironment environment;
    private ApplicationSyncPolicy policy;
    private ManagedApplicationSyncTransport transport;
    private AccountApplication app;
    private ApplicationSyncDelivery command;
    private final ObjectMapper json = new ObjectMapper();
    private final HmacSignatureService signatures = new HmacSignatureService();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>(validResult());
    private final List<String> nonces = new ArrayList<>();
    private final List<String> bodies = new ArrayList<>();
    private final List<String> signaturesReceived = new ArrayList<>();

    @Test
    void enablesReliableDeliveryByDefaultWithoutInventingTargets() {
        var defaults = new ApplicationSyncProperties();
        assertThat(defaults.isEnabled()).isTrue();
        assertThat(defaults.getTargets()).isEmpty();
    }

    @BeforeEach
    void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/account-integration/users/user-1", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            bodies.add(body);
            var headers = exchange.getRequestHeaders();
            String nonce = headers.getFirst(AccountIntegrationHeaders.NONCE);
            nonces.add(nonce);
            signaturesReceived.add(headers.getFirst(AccountIntegrationHeaders.SIGNATURE));
            String canonical = "PUT\n/account-integration/users/user-1\n"
                    + headers.getFirst(AccountIntegrationHeaders.TIMESTAMP) + "\n" + nonce + "\n" + body;
            boolean signed = signatures.verify(canonical, "synthetic-secret", headers.getFirst(AccountIntegrationHeaders.SIGNATURE));
            byte[] response = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + server.getAddress().getPort() + "/must-not-follow");
            exchange.sendResponseHeaders(signed ? responseStatus.get() : 401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        properties = new ApplicationSyncProperties();
        properties.setEnabled(true);
        properties.getTargets().put("app-1", URI.create(base));
        environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        policy = new ApplicationSyncPolicy(properties, environment);
        transport = new ManagedApplicationSyncTransport(policy, json, signatures, Clock.systemUTC());
        app = new AccountApplication("app-1", "Synthetic", base, base + "/callback", base + "/permissions",
                base, "synthetic-secret", "default");
        command = new ApplicationSyncDelivery();
        command.setAppCode("app-1");
        command.setUserId("user-1");
        command.setDesiredStatus("enabled");
        command.setSyncVersion(7);
        command.setIdempotencyKey("fixed-command-key");
        command.setPayloadJson("{\"appCode\":\"app-1\",\"globalUserId\":\"user-1\",\"desiredStatus\":\"enabled\",\"syncVersion\":7}");
    }

    @AfterEach
    void stop() { if (server != null) server.stop(0); }

    @Test
    void sendsFixedBodyWithFreshSignedNonceAndAcceptsExactConfirmation() {
        assertThat(transport.send(command, app).getLocalUserId()).isEqualTo("local-1");
        responseBody.set(validResult().replace("APPLIED", "ALREADY_APPLIED"));
        assertThat(transport.send(command, app).getAppliedVersion()).isEqualTo(7);
        assertThat(bodies).containsExactly(command.getPayloadJson(), command.getPayloadJson());
        assertThat(nonces).hasSize(2).doesNotHaveDuplicates();
        assertThat(signaturesReceived).doesNotHaveDuplicates();
    }

    @Test
    void rejectsMismatchedResponseFieldsAndInvalidJson() {
        for (String body : List.of(validResult().replace("app-1", "wrong-app"),
                validResult().replace("user-1", "wrong-user"), validResult().replace("enabled", "disabled"),
                validResult().replace(":7", ":8"), validResult().replace(":7", ":6"),
                validResult().replace(":7", ":7.1"), validResult().replace(":7", ":\"7\""),
                validResult().replace("local-1", ""), validResult().replace("local-1", "x".repeat(256)),
                validResult().replace("\"local-1\"", "1"),
                validResult().replace("APPLIED", "ACCEPTED"), validResult().replace("\"APPLIED\"", "null"),
                "null", "not-json")) {
            responseBody.set(body);
            assertThatThrownBy(() -> transport.send(command, app)).isInstanceOf(ApplicationSyncFailure.class)
                    .hasMessage("SYNC_RESULT_INVALID");
        }
    }

    @Test
    void rejectsRedirectAndOversizedResponseAndRetriesOnlyTransientFailures() {
        responseStatus.set(302);
        assertThatThrownBy(() -> transport.send(command, app)).isInstanceOf(ApplicationSyncFailure.class)
                .hasMessage("SYNC_RESPONSE_REJECTED");
        assertThat(bodies).hasSize(1);
        responseStatus.set(200);
        responseBody.set("x".repeat(65537));
        assertThatThrownBy(() -> transport.send(command, app)).isInstanceOf(ApplicationSyncFailure.class)
                .hasMessage("SYNC_RESPONSE_TOO_LARGE");
        responseBody.set("{}");
        for (int code : new int[]{429, 500, 503}) {
            responseStatus.set(code);
            assertThatThrownBy(() -> transport.send(command, app)).isInstanceOfSatisfying(ApplicationSyncFailure.class,
                    failure -> assertThat(failure.isRetryable()).isTrue());
        }
        responseStatus.set(409);
        assertThatThrownBy(() -> transport.send(command, app)).isInstanceOfSatisfying(ApplicationSyncFailure.class,
                failure -> assertThat(failure.isRetryable()).isFalse());
    }

    @Test
    void requiresExplicitMatchingTargetAndAllowsHttpOnlyForLiteralLoopbackInTests() {
        environment.setActiveProfiles("production");
        assertThat(policy.canDeliver(app)).isFalse();
        environment.setActiveProfiles("test");
        assertThat(policy.canDeliver(app)).isTrue();
        properties.setEnabled(false);
        assertThat(policy.canDeliver(app)).isFalse();
        properties.setEnabled(true);
        for (String target : List.of("http://localhost", "https://user@example.invalid", "https://example.invalid?a=1",
                "https://example.invalid#fragment", "https://example.invalid/a/../b", "https://example.invalid/%61")) {
            app.setNotifyBaseUrl(target);
            properties.getTargets().put("app-1", URI.create(target));
            assertThat(policy.canDeliver(app)).as(target).isFalse();
        }
        properties.getTargets().put("app-1", URI.create("https://example.invalid"));
        app.setNotifyBaseUrl("https://different.invalid");
        assertThat(policy.canDeliver(app)).isFalse();
    }

    @Test
    void syntheticProviderPersistsReceiptBeforeLostResponseAndReplaysAfterHandlerRestart() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:provider_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        var ledger = new JdbcTemplate(source);
        ledger.execute("create table provider_receipts (command_key varchar(64) primary key, body text not null, receipt text not null)");
        // 合成提供方先提交幂等结果，再模拟回包丢失；重新创建 Handler 后依赖数据库而非内存。
        ledger.update("insert into provider_receipts values (?, ?, ?)", command.getIdempotencyKey(), command.getPayloadJson(), validResult());
        responseStatus.set(503);
        assertThatThrownBy(() -> transport.send(command, app)).hasMessage("SYNC_DEPENDENCY_UNAVAILABLE");
        var restartedLedger = new JdbcTemplate(source);
        var receipt = restartedLedger.queryForMap("select body, receipt from provider_receipts where command_key = ?", command.getIdempotencyKey());
        assertThat(receipt.get("BODY").toString()).isEqualTo(command.getPayloadJson());
        responseBody.set(receipt.get("RECEIPT").toString().replace("APPLIED", "ALREADY_APPLIED"));
        responseStatus.set(200);
        assertThat(transport.send(command, app).getLocalUserId()).isEqualTo("local-1");
        assertThat(restartedLedger.queryForObject("select count(*) from provider_receipts", Integer.class)).isEqualTo(1);
    }

    private static String validResult() {
        return "{\"appCode\":\"app-1\",\"globalUserId\":\"user-1\",\"localUserId\":\"local-1\","
                + "\"appliedStatus\":\"enabled\",\"appliedVersion\":7,\"resultCode\":\"APPLIED\"}";
    }
}
