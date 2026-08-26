package com.hypers.account.starter.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.starter.sign.AccountHmacSigner;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountProviderClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<String> capturedAppCode = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/openapi/sso/tickets/exchange", exchange -> respond(exchange, 200,
                "{\"externalUserId\":\"user-1\",\"account\":\"user.one\",\"name\":\"合成用户\",\"tenantCode\":\"default\"}"));
        server.createContext("/openapi/admin-tickets/verify", exchange -> respond(exchange, 503,
                "{\"code\":\"DEPENDENCY_UNAVAILABLE\"}"));
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void exchangesTicketWithSignedAccountProviderRequest() {
        AccountSsoClient client = new AccountSsoClient(providerClient());

        var response = client.exchange("ticket-code");

        assertThat(response.getExternalUserId()).isEqualTo("user-1");
        assertThat(capturedAppCode).hasValue("synthetic-app");
    }

    @Test
    void mapsNonSuccessStatusWithoutExposingResponseBody() {
        AccountAdminTicketClient client = new AccountAdminTicketClient(providerClient());

        assertThatThrownBy(() -> client.verify("admin-ticket"))
                .isInstanceOf(AccountClientException.class)
                .hasMessage("Account provider rejected the request")
                .extracting("statusCode")
                .isEqualTo(503);
        assertThatThrownBy(() -> client.verify("admin-ticket"))
                .isInstanceOf(AccountClientException.class)
                .extracting("code")
                .isEqualTo("DEPENDENCY_UNAVAILABLE");
    }

    private AccountProviderClient providerClient() {
        return new AccountProviderClient(
                "http://127.0.0.1:" + port,
                "synthetic-app",
                "synthetic-secret",
                Duration.ofSeconds(2),
                new AccountHmacSigner(),
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        capturedAppCode.set(exchange.getRequestHeaders().getFirst(AccountIntegrationHeaders.APP_CODE));
        assertThat(exchange.getRequestHeaders().getFirst(AccountIntegrationHeaders.SIGNATURE)).isNotBlank();
        exchange.getRequestBody().readAllBytes();
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
