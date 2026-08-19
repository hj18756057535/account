package com.hypers.account.web;

import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.auth.AdminAuthorizationService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

public class AdminSessionInterceptor implements HandlerInterceptor {

    private final AdminAuthorizationService authorizationService;

    public AdminSessionInterceptor(AdminAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        AccountSessionUser sessionUser = session == null
                ? null
                : (AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY);
        if (sessionUser == null) {
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
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }

    private boolean hasRequiredRole(HttpServletRequest request, AccountSessionUser sessionUser) {
        String userId = sessionUser.getUserId();
        if (request.getRequestURI().startsWith("/audit-logs")) {
            return authorizationService.isAdmin(userId) || authorizationService.isAuditor(userId);
        }
        return authorizationService.isAdmin(userId);
    }
}
