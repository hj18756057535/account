package com.hypers.account.app.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.ApplicationMenuPermissionFailure;
import com.hypers.account.app.ApplicationSyncPolicy;
import com.hypers.account.config.ApplicationSyncProperties;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.contract.AccountIntegrationPaths;
import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.security.HmacSignatureService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class HttpApplicationMenuPermissionClientTest {

    private static final String SECRET = "synthetic-secret";
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final HmacSignatureService signatures = new HmacSignatureService();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> response = new AtomicReference<>(validSnapshot());
    private final List<String> idempotencyKeys = new ArrayList<>();
    private final List<String> nonces = new ArrayList<>();
    private HttpServer server;
    private HttpApplicationMenuPermissionClient client;
    private AccountApplication application;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(AccountIntegrationPaths.MENU_PERMISSION_QUERY, this::handle);
        server.createContext(AccountIntegrationPaths.MENU_PERMISSION_REPLACE, this::handle);
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        ApplicationSyncProperties properties = new ApplicationSyncProperties();
        properties.getTargets().put("app-1", URI.create(base));
        MockEnvironment environment = new MockEnvironment().withProperty("account.store.type", "mybatis");
        environment.setActiveProfiles("test");
        client = new HttpApplicationMenuPermissionClient(
                new ApplicationSyncPolicy(properties, environment), json, signatures, Clock.systemUTC());
        application = new AccountApplication("app-1", "Synthetic", base, base + "/callback",
                base + "/permissions", base, SECRET, "default");
        application.setStatus("enabled");
        application.setSecretState("active");
        application.setProtocolCapabilities("user_sync,menu_permission_v1");
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    @Test
    void querySendsExactSignedProtocolRequest() {
        var result = client.query(application, MenuPermissionQuery.builder()
                .protocolVersion("menu_permission_v1").appCode("app-1").globalUserId("user-1")
                .localUserId("local-1").account("alice").locale("zh-CN").build());

        assertThat(result.getCatalogRevision()).isEqualTo("catalog-1");
        assertThat(result.getNodes()).extracting("code").containsExactly("group", "menu");
        assertThat(requests).hasValue(1);
        assertThat(idempotencyKeys).containsExactly((String) null);
    }

    @Test
    void replaceRetriesOnceWithSameIdempotencyKeyAndFreshNonce() {
        status.set(503);
        response.set("{\"code\":\"PERMISSION_PROVIDER_UNAVAILABLE\"}");

        assertThatThrownBy(() -> client.replace(application, "idem-1", MenuPermissionReplaceCommand.builder()
                .protocolVersion("menu_permission_v1").appCode("app-1").globalUserId("user-1")
                .localUserId("local-1").account("alice").locale("zh-CN")
                .expectedCatalogRevision("catalog-1").expectedPermissionRevision("permission-1")
                .selectedCodes(List.of("menu")).build()))
                .isInstanceOf(ApplicationMenuPermissionFailure.class)
                .hasMessage("PERMISSION_PROVIDER_UNAVAILABLE");

        assertThat(requests).hasValue(2);
        assertThat(idempotencyKeys).containsExactly("idem-1", "idem-1");
        assertThat(nonces).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void rejectsInvalidSuccessfulSnapshotAsUnavailable() {
        response.set(validSnapshot().replace("\"parentCode\":\"group\"", "\"parentCode\":\"missing\""));

        assertThatThrownBy(() -> client.query(application, MenuPermissionQuery.builder()
                .protocolVersion("menu_permission_v1").appCode("app-1").globalUserId("user-1")
                .localUserId("local-1").account("alice").locale("en-US").build()))
                .isInstanceOf(ApplicationMenuPermissionFailure.class)
                .hasMessage("PERMISSION_PROVIDER_UNAVAILABLE");
    }

    @Test
    void rejectsUnknownFieldsAndMismatchedProviderStatusCodes() {
        String valid = validSnapshot();
        response.set(valid.substring(0, valid.length() - 1) + ",\"unexpected\":true}");
        assertThatThrownBy(() -> client.query(application, query()))
                .isInstanceOf(ApplicationMenuPermissionFailure.class)
                .hasMessage("PERMISSION_PROVIDER_UNAVAILABLE");

        status.set(404);
        response.set("{\"code\":\"PERMISSION_REVISION_CONFLICT\"}");
        assertThatThrownBy(() -> client.query(application, query()))
                .isInstanceOf(ApplicationMenuPermissionFailure.class)
                .satisfies(error -> assertThat(((ApplicationMenuPermissionFailure) error).getStatus()).isEqualTo(503));
    }

    private MenuPermissionQuery query() {
        return MenuPermissionQuery.builder().protocolVersion("menu_permission_v1").appCode("app-1")
                .globalUserId("user-1").localUserId("local-1").account("alice").locale("en-US").build();
    }

    private void handle(HttpExchange exchange) {
        try {
            requests.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String nonce = exchange.getRequestHeaders().getFirst(AccountIntegrationHeaders.NONCE);
            String path = exchange.getRequestURI().getRawPath();
            String canonical = exchange.getRequestMethod() + "\n" + path + "\n"
                    + exchange.getRequestHeaders().getFirst(AccountIntegrationHeaders.TIMESTAMP) + "\n"
                    + nonce + "\n" + body;
            boolean valid = signatures.verify(canonical, SECRET,
                    exchange.getRequestHeaders().getFirst(AccountIntegrationHeaders.SIGNATURE));
            nonces.add(nonce);
            idempotencyKeys.add(exchange.getRequestHeaders().getFirst(AccountIntegrationHeaders.IDEMPOTENCY_KEY));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(valid ? status.get() : 401, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String validSnapshot() {
        return "{\"catalogRevision\":\"catalog-1\",\"permissionRevision\":\"permission-1\","
                + "\"assignmentMode\":\"ADDITIVE\",\"nodes\":["
                + "{\"code\":\"menu\",\"parentCode\":\"group\",\"nodeType\":\"MENU\","
                + "\"defaultName\":\"Menu\",\"localizedNames\":{},\"assignable\":true,\"sort\":2},"
                + "{\"code\":\"group\",\"parentCode\":null,\"nodeType\":\"GROUP\","
                + "\"defaultName\":\"Group\",\"localizedNames\":{},\"assignable\":false,\"sort\":1}],"
                + "\"selectedCodes\":[\"menu\"],\"inheritedCodes\":[]}";
    }
}
