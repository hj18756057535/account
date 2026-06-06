package com.hypers.account.starter.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.starter.dto.SsoTicketExchangeRequest;
import com.hypers.account.starter.dto.SsoTicketExchangeResponse;
import com.hypers.account.starter.sign.AccountHmacSigner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Account Center SSO 客户端。
 * 应用后端收到 SSO callback 后，使用此客户端向 Account Center 兑换用户信息。
 */
public class AccountSsoClient {

    private final String accountCenterBaseUrl;
    private final String appCode;
    private final String secret;
    private final AccountHmacSigner signer;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AccountSsoClient(String accountCenterBaseUrl, String appCode, String secret) {
        this.accountCenterBaseUrl = accountCenterBaseUrl;
        this.appCode = appCode;
        this.secret = secret;
        this.signer = new AccountHmacSigner();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = new ObjectMapper();
    }

    /** 用 SSO code 向 Account Center 兑换用户信息 */
    public SsoTicketExchangeResponse exchange(String code) {
        try {
            SsoTicketExchangeRequest request = new SsoTicketExchangeRequest(appCode, code);
            String body = objectMapper.writeValueAsString(request);
            String path = "/openapi/sso/tickets/exchange";
            String timestamp = String.valueOf(System.currentTimeMillis());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signText = signer.buildSignText("POST", path, timestamp, nonce, body);
            String signature = signer.sign(signText, secret);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(accountCenterBaseUrl + path))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Account-App-Code", appCode)
                    .header("X-Account-Timestamp", timestamp)
                    .header("X-Account-Nonce", nonce)
                    .header("X-Account-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("SSO ticket exchange failed: " + response.statusCode() + " " + response.body());
            }
            return objectMapper.readValue(response.body(), SsoTicketExchangeResponse.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SSO ticket exchange request error: " + e.getMessage(), e);
        }
    }
}
