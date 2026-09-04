package com.hypers.account.web;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class SsoRedirectValidationInterceptor implements HandlerInterceptor {

    private final AccountDirectoryService directoryService;

    public SsoRedirectValidationInterceptor(AccountDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String appCode = request.getParameter("appCode");
        String redirectUri = request.getParameter("redirectUri");
        if (appCode == null || redirectUri == null) {
            return true;
        }
        try {
            AccountApplication application = directoryService.getApplication(appCode);
            if (!redirectUri.equals(application.getSsoCallbackUrl())) {
                response.sendRedirect("/sso/error?code=callbackMismatch");
                return false;
            }
        } catch (IllegalArgumentException ignored) {
            return true;
        }
        return true;
    }
}
