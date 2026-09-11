package com.hypers.account.web;

import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.auth.AdminAuthorizationService;
import com.hypers.account.web.management.ApiErrorWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class AdminSessionInterceptor implements HandlerInterceptor {

    private final AdminAuthorizationService authorizationService;
    private final ApiErrorWriter apiErrorWriter;
    private final LegacyApiErrorWriter legacyErrors;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        AccountSessionUser sessionUser = session == null
                ? null
                : (AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY);
        if (sessionUser == null) {
            if (isConsoleApiRequest(request.getRequestURI())) {
                apiErrorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED", "error.authentication");
                return false;
            }
            if (request.getRequestURI().startsWith("/api/")) {
                legacyErrors.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "error.authentication");
            } else {
                response.sendRedirect("/login");
            }
            return false;
        }
        if (hasRequiredRole(request, sessionUser)) {
            return true;
        }
        if (isConsoleApiRequest(request.getRequestURI())) {
            apiErrorWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                    "ACCESS_DENIED", "error.accessDenied");
            return false;
        }
        if (request.getRequestURI().startsWith("/api/")) {
            legacyErrors.write(request, response, HttpServletResponse.SC_FORBIDDEN, "error.accessDenied");
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
        return false;
    }

    private boolean hasRequiredRole(HttpServletRequest request, AccountSessionUser sessionUser) {
        String userId = sessionUser.getUserId();
        String requestUri = request.getRequestURI();
        if (requestUri.matches("/api/users/[^/]+/applications/[^/]+/menu-permissions")) {
            return authorizationService.isAdmin(userId);
        }
        if (isManagementReadRequest(request.getMethod(), requestUri)) {
            return authorizationService.canReadUsers(userId);
        }
        if (request.getRequestURI().startsWith("/audit-logs")) {
            return authorizationService.isAdmin(userId) || authorizationService.isAuditor(userId);
        }
        return authorizationService.isAdmin(userId);
    }

    private boolean isConsoleApiRequest(String requestUri) {
        return "/api/session".equals(requestUri)
                || requestUri.startsWith("/api/user-imports/")
                || "/api/users".equals(requestUri)
                || requestUri.startsWith("/api/users/")
                || "/api/applications".equals(requestUri)
                || requestUri.startsWith("/api/applications/")
                || "/api/audit-events".equals(requestUri)
                || requestUri.startsWith("/api/audit-events/");
    }

    private boolean isManagementReadRequest(String method, String requestUri) {
        if (!"GET".equals(method)) {
            return false;
        }
        if ("/api/audit-events".equals(requestUri) || requestUri.startsWith("/api/audit-events/")) {
            return true;
        }
        if ("/api/applications".equals(requestUri) || requestUri.startsWith("/api/applications/")) {
            return true;
        }
        if (requestUri.matches("/api/users/[^/]+/application-access")) {
            return true;
        }
        if ("/api/users".equals(requestUri)) {
            return true;
        }
        String detailPrefix = "/api/users/";
        return requestUri.startsWith(detailPrefix)
                && requestUri.indexOf('/', detailPrefix.length()) < 0;
    }
}
