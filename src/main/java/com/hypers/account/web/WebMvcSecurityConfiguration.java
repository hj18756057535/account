package com.hypers.account.web;

import com.hypers.account.auth.AdminAuthorizationService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcSecurityConfiguration implements WebMvcConfigurer {

    private final AdminAuthorizationService authorizationService;

    public WebMvcSecurityConfiguration(AdminAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminSessionInterceptor(authorizationService))
                .addPathPatterns(
                        "/api/**",
                        "/users",
                        "/applications",
                        "/audit-logs");
    }
}
