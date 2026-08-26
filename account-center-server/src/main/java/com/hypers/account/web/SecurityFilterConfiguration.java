package com.hypers.account.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.security.OpenApiSignatureVerifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityFilterConfiguration {

    @Bean
    public FilterRegistrationBean<OpenApiSignatureFilter> openApiSignatureFilter(
            OpenApiSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<OpenApiSignatureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OpenApiSignatureFilter(signatureVerifier, objectMapper));
        registration.addUrlPatterns("/openapi/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AdminTicketIssueValidationFilter> adminTicketIssueValidationFilter(
            AccountDirectoryService directoryService,
            ObjectMapper objectMapper) {
        FilterRegistrationBean<AdminTicketIssueValidationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AdminTicketIssueValidationFilter(directoryService, objectMapper));
        registration.addUrlPatterns("/api/admin-tickets");
        registration.setOrder(2);
        return registration;
    }
}
