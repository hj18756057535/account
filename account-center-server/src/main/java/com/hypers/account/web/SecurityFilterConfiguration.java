package com.hypers.account.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.service.ApplicationManagementService;
import com.hypers.account.app.service.UserApplicationAccessService;
import com.hypers.account.app.service.UserManagementService;
import com.hypers.account.security.OpenApiSignatureVerifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityFilterConfiguration {

    @Bean
    public FilterRegistrationBean<OpenApiSignatureFilter> openApiSignatureFilter(
            OpenApiSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper, LegacyApiErrorWriter errors) {
        FilterRegistrationBean<OpenApiSignatureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OpenApiSignatureFilter(signatureVerifier, objectMapper, errors));
        registration.addUrlPatterns("/openapi/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AdminTicketIssueValidationFilter> adminTicketIssueValidationFilter(
            UserManagementService userService,
            ApplicationManagementService applicationService,
            UserApplicationAccessService accessService,
            ObjectMapper objectMapper, LegacyApiErrorWriter errors) {
        FilterRegistrationBean<AdminTicketIssueValidationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AdminTicketIssueValidationFilter(
                userService, applicationService, accessService, objectMapper, errors));
        registration.addUrlPatterns("/api/admin-tickets");
        registration.setOrder(2);
        return registration;
    }
}
