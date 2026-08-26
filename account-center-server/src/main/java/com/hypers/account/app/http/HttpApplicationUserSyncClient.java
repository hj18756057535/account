package com.hypers.account.app.http;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.ApplicationUserSyncClient;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.contract.AccountIntegrationPaths;
import com.hypers.account.contract.application.AccountUserDesiredState;
import com.hypers.account.contract.application.AccountUserSyncResult;
import com.hypers.account.security.HmacSignatureService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;

/**
 * 真实 HTTP 同步客户端。
 * 授权/取消授权时，通过 HMAC 签名的 HTTP PUT 通知应用应用用户期望状态。
 * 签名头：X-Account-App-Code, X-Account-Timestamp, X-Account-Nonce, X-Account-Signature
 * 签名原文：method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body
 */
@RequiredArgsConstructor
public class HttpApplicationUserSyncClient implements ApplicationUserSyncClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final HmacSignatureService signatureService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AtomicLong syncVersionSequence = new AtomicLong();

    public HttpApplicationUserSyncClient(HmacSignatureService signatureService,
                                         ObjectMapper objectMapper,
                                         Clock clock) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void upsert(AccountApplication application, AccountUser user) {
        sendRequest(application, user, "enabled");
    }

    @Override
    public void disable(AccountApplication application, AccountUser user) {
        sendRequest(application, user, "disabled");
    }

    /**
     * 发送 HMAC 签名的 HTTP POST 请求到应用。
     * 生成 nonce 防重放，签名保证请求完整性。
     */
    private void sendRequest(AccountApplication application, AccountUser user, String desiredStatus) {
        try {
            long syncVersion = nextSyncVersion();
            Instant occurredAt = clock.instant();
            AccountUserDesiredState desiredState = AccountUserDesiredState.builder()
                    .appCode(application.getAppCode())
                    .globalUserId(user.getId())
                    .account(user.getAccount())
                    .displayName(user.getName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .tenantCode(application.getDefaultTenantCode())
                    .desiredStatus(desiredStatus)
                    .syncVersion(syncVersion)
                    .occurredAt(occurredAt)
                    .build();
            String body = objectMapper.writeValueAsString(desiredState);
            String path = AccountIntegrationPaths.applicationUser(user.getId());
            String timestamp = String.valueOf(clock.millis());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signText = "PUT\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body;
            String signature = signatureService.sign(signText, application.getSecret());

            URI uri = URI.create(normalizedBaseUrl(application.getNotifyBaseUrl()) + path);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header(AccountIntegrationHeaders.IDEMPOTENCY_KEY,
                            application.getAppCode() + ':' + user.getId() + ':' + syncVersion)
                    .header(AccountIntegrationHeaders.APP_CODE, application.getAppCode())
                    .header(AccountIntegrationHeaders.TIMESTAMP, timestamp)
                    .header(AccountIntegrationHeaders.NONCE, nonce)
                    .header(AccountIntegrationHeaders.SIGNATURE, signature)
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApplicationSyncException(
                        "应用 " + application.getAppCode() + " 同步失败，状态码: " + response.statusCode(),
                        response.statusCode());
            }
            AccountUserSyncResult result = objectMapper.readValue(response.body(), AccountUserSyncResult.class);
            if (!application.getAppCode().equals(result.getAppCode())
                    || !user.getId().equals(result.getGlobalUserId())
                    || result.getLocalUserId() == null
                    || result.getLocalUserId().isBlank()
                    || !desiredStatus.equals(result.getAppliedStatus())
                    || result.getAppliedVersion() < syncVersion
                    || result.getResultCode() == null
                    || result.getResultCode().isBlank()) {
                throw new ApplicationSyncException("应用返回的同步结果不符合契约");
            }
        } catch (ApplicationSyncException e) {
            throw e;
        } catch (Exception e) {
            throw new ApplicationSyncException(
                    "应用 " + application.getAppCode() + " 同步请求异常: " + e.getMessage(), e);
        }
    }

    private long nextSyncVersion() {
        return syncVersionSequence.updateAndGet(previous -> Math.max(previous + 1, clock.millis()));
    }

    private String normalizedBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
