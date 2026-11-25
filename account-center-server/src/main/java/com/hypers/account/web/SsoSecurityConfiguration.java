package com.hypers.account.web;

import com.hypers.account.app.service.ApplicationManagementService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册 SSO 回调地址校验拦截器。 */
@Configuration
public class SsoSecurityConfiguration implements WebMvcConfigurer {

    private final ApplicationManagementService applicationService;

    public SsoSecurityConfiguration(ApplicationManagementService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SsoRedirectValidationInterceptor(applicationService))
                .addPathPatterns("/sso/authorize");
    }
}
