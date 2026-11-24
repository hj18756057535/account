package com.hypers.account.app.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.ApplicationSyncPolicy;
import com.hypers.account.config.ApplicationSyncProperties;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.contract.AccountProviderProtocol;
import com.hypers.account.security.HmacSignatureService;
import com.hypers.account.web.management.ApiException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ApplicationDirectoryClientTest {
    private HttpServer server;
    private AccountApplication app;
    private ApplicationDirectoryClient client;
    private final AtomicBoolean numericId = new AtomicBoolean();
    private final AtomicBoolean authenticated = new AtomicBoolean();

    @BeforeEach
    void setup() throws Exception {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        HmacSignatureService signatures = new HmacSignatureService();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        app = new AccountApplication("cms-ai", "CMS", "http://localhost:8850", "http://localhost:8850/callback",
                "", "http://127.0.0.1:" + server.getAddress().getPort(), "synthetic-secret", "default");

        server.createContext(AccountProviderProtocol.DIRECTORY_PATH, exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            var headers = exchange.getRequestHeaders();
            authenticated.set(headers.getFirst("Authorization") == null
                    && "cms-ai".equals(headers.getFirst(AccountIntegrationHeaders.APP_CODE))
                    && AccountProviderProtocol.VERSION.equals(headers.getFirst(AccountIntegrationHeaders.PROTOCOL_VERSION))
                    && signatures.verify("POST\n" + AccountProviderProtocol.DIRECTORY_PATH + "\n"
                            + headers.getFirst(AccountIntegrationHeaders.TIMESTAMP) + "\n"
                            + headers.getFirst(AccountIntegrationHeaders.NONCE) + "\n" + body,
                            app.getSecret(), headers.getFirst(AccountIntegrationHeaders.SIGNATURE)));
            String kind = json.readTree(body).path("kind").asText();
            String id = numericId.get() ? "9007199254740993" : "\"9007199254740993\"";
            String users = "users".equals(kind) ? "[{\"localUserId\":" + id
                    + ",\"account\":\"fixture-user\",\"status\":\"enabled\",\"roleIds\":[],\"organizationIds\":[]}]" : "[]";
            byte[] response = ("{\"appCode\":\"cms-ai\",\"tenantCode\":\"default\",\"kind\":\"" + kind
                    + "\",\"users\":" + users + ",\"roles\":[],\"organizations\":[]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new ApplicationDirectoryClient(new ApplicationSyncPolicy(new ApplicationSyncProperties(),
                new MockEnvironment()), json, signatures, Clock.systemUTC());
    }

    @AfterEach
    void close() {
        if (server != null) server.stop(0);
    }

    @Test
    void fetchUsesApplicationHmacAndPreservesLargeSourceIdsWithoutForwardingBrowserCredentials() {
        ApplicationDirectorySnapshot result = client.fetch(app);
        assertThat(authenticated).isTrue();
        assertThat(result.getUsers().getFirst().getLocalUserId()).isEqualTo("9007199254740993");
    }

    @Test
    void numericWireIdsAreRejectedRatherThanCoerced() {
        numericId.set(true);
        assertThatThrownBy(() -> client.check(app)).isInstanceOf(ApiException.class);
    }
}
