package com.hypers.account.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.security.OpenApiSignatureVerifier;
import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OpenApiSignatureFilter extends OncePerRequestFilter {

    private final OpenApiSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    private final LegacyApiErrorWriter errors;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, bodyBytes);
        String body = wrapped.getCachedBodyAsString();
        try {
            AccountApplication application = signatureVerifier.verify(wrapped, body, request.getRequestURI());
            JsonNode json = objectMapper.readTree(body);
            JsonNode bodyAppCode = json == null ? null : json.get("appCode");
            if (bodyAppCode == null || !application.getAppCode().equals(bodyAppCode.asText())) {
                errors.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "error.signature");
                return;
            }
        } catch (ResponseStatusException e) {
            errors.write(request, response, e.getStatusCode().value(), "error.signature");
            return;
        } catch (IllegalArgumentException | JsonProcessingException e) {
            errors.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "error.signature");
            return;
        }
        filterChain.doFilter(wrapped, response);
    }
}
