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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        AccountSessionUser sessionUser = session == null
                ? null
                : (AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY);
        if (sessionUser == null) {
            if (isConsoleApiRequest(request.getRequestURI())) {
                apiErrorWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED", "登录状态已失效，请重新登录");
                return false;
            }
            if (request.getRequestURI().startsWith("/api/")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
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
                    "ACCESS_DENIED", "当前账号没有执行此操作的权限");
            return false;
        }
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }

    private boolean hasRequiredRole(HttpServletRequest request, AccountSessionUser sessionUser) {
        String userId = sessionUser.getUserId();
        String requestUri = request.getRequestURI();
        if (isUserReadRequest(request.getMethod(), requestUri)) {
            return authorizationService.canReadUsers(userId);
        }
        if (request.getRequestURI().startsWith("/audit-logs")) {
            return authorizationService.isAdmin(userId) || authorizationService.isAuditor(userId);
        }
        return authorizationService.isAdmin(userId);
    }

    private boolean isConsoleApiRequest(String requestUri) {
        return "/api/session".equals(requestUri)
                || "/api/users".equals(requestUri)
                || requestUri.startsWith("/api/users/");
    }

    private boolean isUserReadRequest(String method, String requestUri) {
        if (!"GET".equals(method)) {
            return false;
        }
        if ("/api/users".equals(requestUri)) {
            return true;
        }
        String detailPrefix = "/api/users/";
        return requestUri.startsWith(detailPrefix)
                && requestUri.indexOf('/', detailPrefix.length()) < 0;
    }
}
