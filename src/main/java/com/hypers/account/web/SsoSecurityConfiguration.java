package com.hypers.account.web;

import com.hypers.account.app.AccountDirectoryService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SsoSecurityConfiguration implements WebMvcConfigurer {

    private final AccountDirectoryService directoryService;

    public SsoSecurityConfiguration(AccountDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SsoRedirectValidationInterceptor(directoryService))
                .addPathPatterns("/sso/authorize");
    }
}
