package com.hypers.account.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.security.OpenApiSignatureVerifier;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

public class OpenApiSignatureFilter extends OncePerRequestFilter {

    private final OpenApiSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    public OpenApiSignatureFilter(OpenApiSignatureVerifier signatureVerifier, ObjectMapper objectMapper) {
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
    }

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
            JsonNode bodyAppCode = json.get("appCode");
            if (bodyAppCode == null || !application.getAppCode().equals(bodyAppCode.asText())) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        } catch (ResponseStatusException e) {
            response.sendError(e.getStatus().value());
            return;
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(wrapped, response);
    }
}
