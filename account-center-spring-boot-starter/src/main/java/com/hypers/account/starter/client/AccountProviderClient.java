package com.hypers.account.starter.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.starter.sign.AccountHmacSigner;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccountProviderClient {

    private final String accountCenterBaseUrl;
    @Getter
    private final String appCode;
    private final String secret;
    private final Duration requestTimeout;
    private final AccountHmacSigner signer;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    <T> T post(String path, Object payload, Class<T> responseType) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            String timestamp = String.valueOf(clock.millis());
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String signature = signer.sign(signer.buildSignText("POST", path, timestamp, nonce, body), secret);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedBaseUrl() + path))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header(AccountIntegrationHeaders.APP_CODE, appCode)
                    .header(AccountIntegrationHeaders.TIMESTAMP, timestamp)
                    .header(AccountIntegrationHeaders.NONCE, nonce)
                    .header(AccountIntegrationHeaders.SIGNATURE, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AccountClientException(
                        "Account provider rejected the request",
                        response.statusCode(),
                        errorCode(response));
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (AccountClientException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AccountClientException("Account provider request was interrupted", exception);
        } catch (Exception exception) {
            throw new AccountClientException("Account provider request failed", exception);
        }
    }

    private String errorCode(HttpResponse<String> response) {
        try {
            String code = objectMapper.readTree(response.body()).path("code").asText();
            return code.isBlank() ? "HTTP_" + response.statusCode() : code;
        } catch (Exception exception) {
            return "HTTP_" + response.statusCode();
        }
    }

    private String normalizedBaseUrl() {
        return accountCenterBaseUrl.endsWith("/")
                ? accountCenterBaseUrl.substring(0, accountCenterBaseUrl.length() - 1)
                : accountCenterBaseUrl;
    }
}
