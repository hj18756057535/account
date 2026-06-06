package com.hypers.account.app.http;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.ApplicationUserSyncClient;
import com.hypers.account.security.HmacSignatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * 真实 HTTP 同步客户端。
 * 授权/取消授权时，通过 HMAC 签名的 HTTP POST 通知应用同步用户数据。
 * 签名头：X-Account-App-Code, X-Account-Timestamp, X-Account-Nonce, X-Account-Signature
 * 签名原文：method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body
 */
public class HttpApplicationUserSyncClient implements ApplicationUserSyncClient {

    private static final String UPSERT_PATH = "/account-sso/internal/users/upsert";
    private static final String DISABLE_PATH = "/account-sso/internal/users/disable";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final HmacSignatureService signatureService;
    private final ObjectMapper objectMapper;

    public HttpApplicationUserSyncClient(HmacSignatureService signatureService) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.signatureService = signatureService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void upsert(AccountApplication application, AccountUser user) {
        ApplicationSyncRequest request = new ApplicationSyncRequest(
                application.getAppCode(),
                application.getDefaultTenantCode(),
                user.getId(),
                user.getAccount(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                true);
        sendRequest(application, UPSERT_PATH, request);
    }

    @Override
    public void disable(AccountApplication application, AccountUser user) {
        ApplicationSyncRequest request = new ApplicationSyncRequest(
                application.getAppCode(),
                application.getDefaultTenantCode(),
                user.getId(),
                user.getAccount(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                false);
        sendRequest(application, DISABLE_PATH, request);
    }

    /**
     * 发送 HMAC 签名的 HTTP POST 请求到应用。
     * 生成 nonce 防重放，签名保证请求完整性。
     */
    private void sendRequest(AccountApplication application, String path, ApplicationSyncRequest syncRequest) {
        try {
            String body = objectMapper.writeValueAsString(syncRequest);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signText = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body;
            String signature = signatureService.sign(signText, application.getSecret());

            URI uri = URI.create(application.getNotifyBaseUrl() + path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-Account-App-Code", application.getAppCode())
                    .header("X-Account-Timestamp", timestamp)
                    .header("X-Account-Nonce", nonce)
                    .header("X-Account-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApplicationSyncException(
                        "应用 " + application.getAppCode() + " 同步失败，状态码: " + response.statusCode(),
                        response.statusCode());
            }
        } catch (ApplicationSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationSyncException(
                    "应用 " + application.getAppCode() + " 同步请求异常: " + e.getMessage(), e);
        }
    }
}
