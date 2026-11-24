package com.hypers.account.boot2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.integration.AccountProviderProtocol;
import com.hypers.account.integration.directory.AccountDirectoryHandler;
import com.hypers.account.integration.menu.AccountNonceStore;
import com.hypers.account.integration.menu.security.AccountHmacSigner;
import com.hypers.account.integration.menu.security.AccountMenuPermissionSignatureFilter;
import com.hypers.account.integration.user.AccountUserSyncHandler;
import com.hypers.account.integration.web.AccountProviderController;
import com.hypers.account.integration.web.AccountProviderExceptionHandler;
import javax.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/** 目录和用户同步共用已登记应用身份，缺少共享防重放或业务 Handler 时启动失败。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "account.integration.provider", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AccountProviderProperties.class)
public class AccountProviderAutoConfiguration {
    @Bean
    public AccountProviderController accountProviderController(AccountProviderProperties properties,
            AccountDirectoryHandler directory, AccountUserSyncHandler users) {
        return new AccountProviderController(properties.getAppCode(), directory, users);
    }

    @Bean
    public AccountProviderExceptionHandler accountProviderExceptionHandler() {
        return new AccountProviderExceptionHandler();
    }

    @Bean
    public FilterRegistrationBean<AccountMenuPermissionSignatureFilter> accountProviderSignatureFilter(
            AccountProviderProperties properties, AccountNonceStore nonces, ObjectMapper mapper) {
        Assert.hasText(properties.getAppCode(), "Provider app-code is required");
        Assert.hasText(properties.getSecret(), "Provider secret is required");
        Assert.isTrue(properties.getAllowedClockSkewMillis() > 0 && properties.getAllowedClockSkewMillis() <= 300000L,
                "Invalid provider signature time window");
        FilterRegistrationBean<AccountMenuPermissionSignatureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AccountMenuPermissionSignatureFilter(properties.getAppCode(), properties.getSecret(),
                properties.getAllowedClockSkewMillis(), nonces, new AccountHmacSigner(), mapper, AccountProviderProtocol.VERSION));
        registration.setName("accountProviderSignatureFilter");
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.addUrlPatterns(AccountProviderProtocol.DIRECTORY_PATH, AccountProviderProtocol.USERS_PATH + "/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
