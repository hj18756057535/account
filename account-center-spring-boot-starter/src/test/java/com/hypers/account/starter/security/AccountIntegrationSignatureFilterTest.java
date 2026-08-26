package com.hypers.account.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.starter.properties.AccountIntegrationProperties;
import com.hypers.account.starter.sign.AccountHmacSigner;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccountIntegrationSignatureFilterTest {

    @Test
    void acceptsValidSignatureAndRejectsReplayedNonce() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T08:00:00Z"), ZoneOffset.UTC);
        AccountIntegrationProperties properties = properties();
        AccountHmacSigner signer = new AccountHmacSigner();
        AccountIntegrationSignatureFilter filter = new AccountIntegrationSignatureFilter(
                properties,
                signer,
                new AccountNonceStore(clock),
                new ObjectMapper(),
                clock);
        String body = "{\"appCode\":\"synthetic-app\"}";
        String timestamp = String.valueOf(clock.millis());
        String nonce = "nonce-0000000001";
        String path = "/account-integration/users/user-1";
        String signature = signer.sign(
                signer.buildSignText("PUT", path, timestamp, nonce, body),
                properties.getSecret());

        MockHttpServletRequest firstRequest = request(path, body, timestamp, nonce, signature);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        MockFilterChain firstChain = new MockFilterChain();
        filter.doFilter(firstRequest, firstResponse, firstChain);

        assertThat(firstResponse.getStatus()).isEqualTo(200);
        assertThat(firstChain.getRequest()).isNotNull();
        assertThat(new String(firstChain.getRequest().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo(body);

        MockHttpServletResponse replayResponse = new MockHttpServletResponse();
        filter.doFilter(request(path, body, timestamp, nonce, signature), replayResponse, new MockFilterChain());

        assertThat(replayResponse.getStatus()).isEqualTo(401);
        assertThat(replayResponse.getContentAsString()).contains("AUTHENTICATION_REQUIRED");
    }

    @Test
    void doesNotConvertBusinessExceptionsIntoAuthenticationFailures() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-24T08:00:00Z"), ZoneOffset.UTC);
        AccountIntegrationProperties properties = properties();
        AccountHmacSigner signer = new AccountHmacSigner();
        AccountIntegrationSignatureFilter filter = new AccountIntegrationSignatureFilter(
                properties,
                signer,
                new AccountNonceStore(clock),
                new ObjectMapper(),
                clock);
        String body = "{\"appCode\":\"synthetic-app\"}";
        String timestamp = String.valueOf(clock.millis());
        String nonce = "nonce-business-001";
        String path = "/account-integration/users/user-1";
        String signature = signer.sign(
                signer.buildSignText("PUT", path, timestamp, nonce, body),
                properties.getSecret());

        assertThatThrownBy(() -> filter.doFilter(
                request(path, body, timestamp, nonce, signature),
                new MockHttpServletResponse(),
                (request, response) -> {
                    throw new IllegalArgumentException("business validation");
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("business validation");
    }

    private AccountIntegrationProperties properties() {
        AccountIntegrationProperties properties = new AccountIntegrationProperties();
        properties.setAccountBaseUrl("http://127.0.0.1:8088");
        properties.setAppCode("synthetic-app");
        properties.setSecret("synthetic-secret");
        properties.setAllowedClockSkew(Duration.ofMinutes(5));
        return properties;
    }

    private MockHttpServletRequest request(
            String path, String body, String timestamp, String nonce, String signature) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", path);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader(AccountIntegrationHeaders.APP_CODE, "synthetic-app");
        request.addHeader(AccountIntegrationHeaders.TIMESTAMP, timestamp);
        request.addHeader(AccountIntegrationHeaders.NONCE, nonce);
        request.addHeader(AccountIntegrationHeaders.SIGNATURE, signature);
        return request;
    }
}
