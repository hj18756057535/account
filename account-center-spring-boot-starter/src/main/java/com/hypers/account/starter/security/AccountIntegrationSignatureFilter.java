package com.hypers.account.starter.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.starter.properties.AccountIntegrationProperties;
import com.hypers.account.starter.sign.AccountHmacSigner;
import com.hypers.account.starter.web.AccountIntegrationErrorResponse;
import com.hypers.account.starter.web.AccountIntegrationMessages;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AccountIntegrationSignatureFilter extends OncePerRequestFilter {

    private final AccountIntegrationProperties properties;
    private final AccountHmacSigner signer;
    private final AccountNonceStore nonceStore;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Content-Language", AccountIntegrationMessages.locale(request).toLanguageTag());
        response.addHeader("Vary", "Accept-Language");
        byte[] body = request.getInputStream().readAllBytes();
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, body);
        try {
            verifyRequest(request, new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            writeUnauthorized(request, response);
            return;
        }
        filterChain.doFilter(wrappedRequest, response);
    }

    private void verifyRequest(HttpServletRequest request, String body) {
        String appCode = requiredHeader(request, AccountIntegrationHeaders.APP_CODE);
        String timestamp = requiredHeader(request, AccountIntegrationHeaders.TIMESTAMP);
        String nonce = requiredHeader(request, AccountIntegrationHeaders.NONCE);
        String signature = requiredHeader(request, AccountIntegrationHeaders.SIGNATURE);
        if (nonce.length() < 16) {
            throw new IllegalArgumentException("invalid nonce");
        }
        if (!properties.getAppCode().equals(appCode)) {
            throw new IllegalArgumentException("unexpected app code");
        }
        long timestampMillis;
        try {
            timestampMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid timestamp", exception);
        }
        Instant requestTime = Instant.ofEpochMilli(timestampMillis);
        Duration age = Duration.between(requestTime, clock.instant()).abs();
        if (age.compareTo(properties.getAllowedClockSkew()) > 0) {
            throw new IllegalArgumentException("expired request");
        }
        String requestPath = request.getRequestURI().substring(request.getContextPath().length());
        String signText = signer.buildSignText(request.getMethod(), requestPath, timestamp, nonce, body);
        if (!signer.verify(signText, properties.getSecret(), signature)) {
            throw new IllegalArgumentException("invalid signature");
        }
        if (!nonceStore.markIfAbsent(appCode, nonce, clock.instant().plus(properties.getAllowedClockSkew()))) {
            throw new IllegalArgumentException("replayed nonce");
        }
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing integration header");
        }
        return value;
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), AccountIntegrationErrorResponse.builder()
                .code("AUTHENTICATION_REQUIRED")
                .message(AccountIntegrationMessages.text(request, "AUTHENTICATION_REQUIRED"))
                .traceId(UUID.randomUUID().toString())
                .build());
    }
}
