package com.hypers.account.integration.menu;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class AccountMenuPermissionSignatureFilter extends OncePerRequestFilter {

    private final String appCode;
    private final String secret;
    private final long allowedClockSkewMillis;
    private final AccountNonceStore nonceStore;
    private final AccountHmacSigner signer;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        setLanguageHeaders(request, response);
        byte[] body = readBody(request.getInputStream());
        if (body == null) {
            writeError(request, response, 413, "PAYLOAD_TOO_LARGE");
            return;
        }
        if (!AccountMenuPermissionProtocol.VERSION.equals(
                request.getHeader(AccountMenuPermissionProtocol.PROTOCOL_VERSION_HEADER))) {
            writeError(request, response, 426, "PROTOCOL_VERSION_UNSUPPORTED");
            return;
        }
        try {
            verify(request, new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            writeError(request, response, 401, "INTEGRATION_AUTH_FAILED");
            return;
        } catch (RuntimeException exception) {
            writeError(request, response, 503, "PERMISSION_PROVIDER_UNAVAILABLE");
            return;
        }
        chain.doFilter(new CachedBodyHttpServletRequest(request, body), response);
    }

    private void verify(HttpServletRequest request, String body) {
        String headerAppCode = required(request, AccountMenuPermissionProtocol.APP_CODE_HEADER);
        String timestamp = required(request, AccountMenuPermissionProtocol.TIMESTAMP_HEADER);
        String nonce = required(request, AccountMenuPermissionProtocol.NONCE_HEADER);
        String signature = required(request, AccountMenuPermissionProtocol.SIGNATURE_HEADER);
        if (!appCode.equals(headerAppCode) || nonce.length() < 16 || nonce.length() > 128) {
            throw new IllegalArgumentException("Invalid integration identity");
        }
        long timestampMillis;
        try {
            timestampMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid timestamp", exception);
        }
        long now = System.currentTimeMillis();
        long skew = Math.max(0L, allowedClockSkewMillis);
        long age;
        try {
            age = Math.subtractExact(now, timestampMillis);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Expired request", exception);
        }
        if (age < -skew || age > skew) {
            throw new IllegalArgumentException("Expired request");
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String signText = signer.buildSignText(request.getMethod(), path, timestamp, nonce, body);
        if (!signer.verify(signText, secret, signature)) {
            throw new IllegalArgumentException("Invalid signature");
        }
        long nonceExpiresAt;
        try {
            nonceExpiresAt = Math.addExact(now, skew);
        } catch (ArithmeticException exception) {
            nonceExpiresAt = Long.MAX_VALUE;
        }
        if (!nonceStore.markIfAbsent(appCode, nonce, nonceExpiresAt)) {
            throw new IllegalArgumentException("Replayed nonce");
        }
    }

    private byte[] readBody(ServletInputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > AccountMenuPermissionProtocol.MAX_REQUEST_BYTES) return null;
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String required(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing integration header");
        }
        return value;
    }

    private void setLanguageHeaders(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Content-Language", AccountIntegrationMessages.language(request));
        response.addHeader("Vary", "Accept-Language");
    }

    private void writeError(HttpServletRequest request,
                            HttpServletResponse response,
                            int status,
                            String code) throws IOException {
        AccountIntegrationErrorResponse error = new AccountIntegrationErrorResponse();
        error.setCode(code);
        error.setMessage(AccountIntegrationMessages.text(request, code));
        error.setTraceId(UUID.randomUUID().toString());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
