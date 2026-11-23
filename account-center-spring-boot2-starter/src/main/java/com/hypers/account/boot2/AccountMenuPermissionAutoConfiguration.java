package com.hypers.account.boot2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.integration.menu.*;
import javax.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "account.integration.menu-permission", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AccountMenuPermissionProperties.class)
public class AccountMenuPermissionAutoConfiguration {

    @Bean
    public AccountMenuPermissionController accountMenuPermissionController(
            AccountMenuPermissionProperties properties, AccountMenuPermissionHandler handler) {
        validate(properties);
        return new AccountMenuPermissionController(properties.getAppCode(), handler);
    }

    @Bean
    public AccountIntegrationExceptionHandler accountIntegrationExceptionHandler() {
        return new AccountIntegrationExceptionHandler();
    }

    @Bean
    public FilterRegistrationBean<AccountMenuPermissionSignatureFilter> accountMenuPermissionSignatureFilter(
            AccountMenuPermissionProperties properties, AccountNonceStore nonceStore, ObjectMapper mapper) {
        validate(properties);
        FilterRegistrationBean<AccountMenuPermissionSignatureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AccountMenuPermissionSignatureFilter(properties.getAppCode(),
                properties.getSecret(), properties.getAllowedClockSkewMillis(), nonceStore,
                new AccountHmacSigner(), mapper));
        registration.setName("accountMenuPermissionSignatureFilter");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.addUrlPatterns(AccountMenuPermissionProtocol.QUERY_PATH, AccountMenuPermissionProtocol.REPLACE_PATH);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    private void validate(AccountMenuPermissionProperties properties) {
        Assert.hasText(properties.getAppCode(), "Account menu permission app-code is required");
        Assert.hasText(properties.getSecret(), "Account menu permission secret is required");
        Assert.isTrue(properties.getAllowedClockSkewMillis() > 0
                && properties.getAllowedClockSkewMillis() <= 300000L,
                "Account menu permission clock skew must be between 1 and 300000 milliseconds");
    }
}
