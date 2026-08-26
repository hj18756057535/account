package com.hypers.account.app.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountUser;
import com.hypers.account.contract.application.AccountUserDesiredState;
import com.hypers.account.security.HmacSignatureService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpApplicationUserSyncClientTest {

    private HttpServer server;
    private int port;
    private final AtomicReference<Map<String, String>> capturedHeaders = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();
        server.createContext("/account-integration/users/", new CapturingHandler(200));
        server.setExecutor(null);
        server.start();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void upsertSendsSignedRequestWithCorrectHeaders() throws Exception {
        HmacSignatureService signatureService = new HmacSignatureService();
        HttpApplicationUserSyncClient client = new HttpApplicationUserSyncClient(
                signatureService, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
        AccountApplication app = new AccountApplication(
                "cms-ai", "双碳服务", "http://localhost:" + port,
                "http://localhost:" + port + "/callback",
                "http://localhost:" + port + "/permissions",
                "http://localhost:" + port + "/", "test-secret", "default");
        AccountUser user = new AccountUser("u-1", "zhangsan", "zhangsan@example.com", "张三", "13800000000");

        client.upsert(app, user);

        Map<String, String> headers = capturedHeaders.get();
        assertThat(headers).isNotNull();
        assertThat(headers.get("X-Account-App-Code")).isEqualTo("cms-ai");
        assertThat(headers.get("X-Account-Timestamp")).isNotEmpty();
        assertThat(headers.get("X-Account-Nonce")).isNotEmpty();
        assertThat(headers.get("X-Account-Signature")).isNotEmpty();

        // 验证签名可校验
        String signText = "PUT\n/account-integration/users/u-1\n"
                + headers.get("X-Account-Timestamp") + "\n"
                + headers.get("X-Account-Nonce") + "\n"
                + capturedBody.get();
        assertThat(signatureService.verify(signText, "test-secret", headers.get("X-Account-Signature"))).isTrue();

        // 验证请求体内容
        ObjectMapper mapper = new ObjectMapper();
        AccountUserDesiredState syncRequest = mapper.findAndRegisterModules()
                .readValue(capturedBody.get(), AccountUserDesiredState.class);
        assertThat(syncRequest.getAppCode()).isEqualTo("cms-ai");
        assertThat(syncRequest.getAccount()).isEqualTo("zhangsan");
        assertThat(syncRequest.getDesiredStatus()).isEqualTo("enabled");
    }

    @Test
    void disableSendsSignedDisableRequest() throws Exception {
        HmacSignatureService signatureService = new HmacSignatureService();
        HttpApplicationUserSyncClient client = new HttpApplicationUserSyncClient(
                signatureService, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC());
        AccountApplication app = new AccountApplication(
                "cms-ai", "双碳服务", "http://localhost:" + port,
                "http://localhost:" + port + "/callback",
                "http://localhost:" + port + "/permissions",
                "http://localhost:" + port, "test-secret", "default");
        AccountUser user = new AccountUser("u-1", "zhangsan", "zhangsan@example.com", "张三", "13800000000");

        client.disable(app, user);

        Map<String, String> headers = capturedHeaders.get();
        assertThat(headers).isNotNull();
        assertThat(headers.get("X-Account-App-Code")).isEqualTo("cms-ai");

        ObjectMapper mapper = new ObjectMapper();
        AccountUserDesiredState syncRequest = mapper.findAndRegisterModules()
                .readValue(capturedBody.get(), AccountUserDesiredState.class);
        assertThat(syncRequest.getDesiredStatus()).isEqualTo("disabled");
    }

    private class CapturingHandler implements HttpHandler {

        private final int responseCode;

        CapturingHandler(int responseCode) {
            this.responseCode = responseCode;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // 捕获请求头
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Account-App-Code", exchange.getRequestHeaders().getFirst("X-Account-App-Code"));
            headers.put("X-Account-Timestamp", exchange.getRequestHeaders().getFirst("X-Account-Timestamp"));
            headers.put("X-Account-Nonce", exchange.getRequestHeaders().getFirst("X-Account-Nonce"));
            headers.put("X-Account-Signature", exchange.getRequestHeaders().getFirst("X-Account-Signature"));
            capturedHeaders.set(headers);

            // 捕获请求体
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            capturedBody.set(body);

            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            AccountUserDesiredState desiredState = mapper.readValue(body, AccountUserDesiredState.class);
            String responseBody = "{\"appCode\":\"" + desiredState.getAppCode()
                    + "\",\"globalUserId\":\"" + desiredState.getGlobalUserId()
                    + "\",\"localUserId\":\"local-u-1\",\"appliedStatus\":\""
                    + desiredState.getDesiredStatus() + "\",\"appliedVersion\":"
                    + desiredState.getSyncVersion() + ",\"resultCode\":\"APPLIED\"}";
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseCode, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}
