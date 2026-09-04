package com.hypers.account.web;

import com.hypers.account.auth.AdminAuthorizationService;
import com.hypers.account.web.management.ApiErrorWriter;
import com.hypers.account.web.management.CsrfInterceptor;
import com.hypers.account.web.management.CsrfTokenManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcSecurityConfiguration implements WebMvcConfigurer {

    private final AdminAuthorizationService authorizationService;
    private final CsrfTokenManager csrfTokenManager;
    private final ApiErrorWriter apiErrorWriter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminSessionInterceptor(authorizationService, apiErrorWriter))
                .addPathPatterns(
                        "/api/**",
                        "/users",
                        "/applications",
                        "/audit-logs")
                .excludePathPatterns("/api/session");
        registry.addInterceptor(new CsrfInterceptor(csrfTokenManager, apiErrorWriter))
                .addPathPatterns(
                        "/api/session",
                        "/api/users",
                        "/api/users/**",
                        "/api/applications",
                        "/api/applications/**");
    }
}
