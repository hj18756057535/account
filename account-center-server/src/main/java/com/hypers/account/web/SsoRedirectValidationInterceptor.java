package com.hypers.account.web;

import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.service.ApplicationManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** 校验 SSO 跳转的回调地址与应用登记一致，防止开放重定向。 */
public class SsoRedirectValidationInterceptor implements HandlerInterceptor {

    private final ApplicationManagementService applicationService;

    public SsoRedirectValidationInterceptor(ApplicationManagementService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String appCode = request.getParameter("appCode");
        String redirectUri = request.getParameter("redirectUri");
        if (appCode == null || redirectUri == null) {
            return true;
        }
        try {
            AccountApplication application = applicationService.getApplication(appCode);
            if (!redirectUri.equals(application.getSsoCallbackUrl())) {
                response.sendRedirect("/sso/error?code=callbackMismatch");
                return false;
            }
        } catch (BusinessException ignored) {
            return true;
        }
        return true;
    }
}
