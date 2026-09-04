package com.hypers.account.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdminTicketIssueValidationFilter extends OncePerRequestFilter {

    private final AccountDirectoryService directoryService;
    private final ObjectMapper objectMapper;

    private final LegacyApiErrorWriter errors;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(AuthController.SESSION_USER_KEY) == null) {
            filterChain.doFilter(request, response);
            return;
        }
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, bodyBytes);
        try {
            JsonNode json = objectMapper.readTree(wrapped.getCachedBodyAsString());
            String appCode = required(json, "appCode");
            String userId = required(json, "userId");
            AccountApplication application = directoryService.getApplication(appCode);
            AccountUser user = directoryService.getUser(userId);
            if ("disabled".equals(application.getStatus())
                    || "disabled".equals(user.getStatus())
                    || !directoryService.isAuthorized(userId, appCode)) {
                errors.write(request, response, HttpServletResponse.SC_BAD_REQUEST, "error.ticketRequest");
                return;
            }
        } catch (IllegalArgumentException | JsonProcessingException e) {
            errors.write(request, response, HttpServletResponse.SC_BAD_REQUEST, "error.ticketRequest");
            return;
        }
        filterChain.doFilter(wrapped, response);
    }

    private String required(JsonNode json, String field) {
        JsonNode value = json == null ? null : json.get(field);
        if (value == null || value.asText().trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText();
    }
}
