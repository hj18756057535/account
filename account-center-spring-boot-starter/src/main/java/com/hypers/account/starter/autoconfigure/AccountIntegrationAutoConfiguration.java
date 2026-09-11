package com.hypers.account.starter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.starter.client.AccountAdminTicketClient;
import com.hypers.account.starter.client.AccountProviderClient;
import com.hypers.account.starter.client.AccountSsoClient;
import com.hypers.account.starter.properties.AccountIntegrationProperties;
import com.hypers.account.starter.security.AccountIntegrationSignatureFilter;
import com.hypers.account.starter.security.AccountNonceStore;
import com.hypers.account.starter.sign.AccountHmacSigner;
import com.hypers.account.starter.spi.AccountMenuPermissionHandler;
import com.hypers.account.starter.spi.AccountUserSyncHandler;
import com.hypers.account.starter.web.AccountMenuPermissionController;
import com.hypers.account.starter.web.AccountIntegrationExceptionHandler;
import com.hypers.account.starter.web.AccountUserSyncController;
import com.hypers.account.starter.web.AccountUserSyncGuard;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;

@AutoConfiguration
@EnableConfigurationProperties(AccountIntegrationProperties.class)
@ConditionalOnProperty(prefix = "account.integration", name = "enabled", havingValue = "true")
public class AccountIntegrationAutoConfiguration {

    @Bean("accountIntegrationClock")
    @ConditionalOnMissingBean(name = "accountIntegrationClock")
    public Clock accountIntegrationClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public AccountHmacSigner accountHmacSigner() {
        return new AccountHmacSigner();
    }

    @Bean("accountIntegrationHttpClient")
    @ConditionalOnMissingBean(name = "accountIntegrationHttpClient")
    public HttpClient accountIntegrationHttpClient(AccountIntegrationProperties properties) {
        return HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
    }

    @Bean
    public AccountProviderClient accountProviderClient(
            AccountIntegrationProperties properties,
            AccountHmacSigner signer,
            @Qualifier("accountIntegrationHttpClient") HttpClient accountIntegrationHttpClient,
            ObjectMapper objectMapper,
            @Qualifier("accountIntegrationClock") Clock accountIntegrationClock) {
        return new AccountProviderClient(
                properties.getAccountBaseUrl(),
                properties.getAppCode(),
                properties.getSecret(),
                properties.getRequestTimeout(),
                signer,
                accountIntegrationHttpClient,
                objectMapper,
                accountIntegrationClock);
    }

    @Bean
    public AccountSsoClient accountSsoClient(AccountProviderClient providerClient) {
        return new AccountSsoClient(providerClient);
    }

    @Bean
    public AccountAdminTicketClient accountAdminTicketClient(AccountProviderClient providerClient) {
        return new AccountAdminTicketClient(providerClient);
    }

    @Bean
    @ConditionalOnBean(AccountUserSyncHandler.class)
    @ConditionalOnProperty(prefix = "account.integration", name = "provider-enabled", havingValue = "true", matchIfMissing = true)
    public AccountUserSyncGuard accountUserSyncGuard() {
        return new AccountUserSyncGuard();
    }

    @Bean
    @ConditionalOnBean(AccountUserSyncHandler.class)
    @ConditionalOnProperty(prefix = "account.integration", name = "provider-enabled", havingValue = "true", matchIfMissing = true)
    public AccountUserSyncController accountUserSyncController(
            AccountIntegrationProperties properties,
            AccountUserSyncHandler handler,
            AccountUserSyncGuard guard) {
        return new AccountUserSyncController(properties, handler, guard);
    }

    @Bean
    @ConditionalOnBean(AccountMenuPermissionHandler.class)
    @ConditionalOnProperty(prefix = "account.integration", name = "provider-enabled", havingValue = "true", matchIfMissing = true)
    public AccountMenuPermissionController accountMenuPermissionController(
            AccountIntegrationProperties properties,
            AccountMenuPermissionHandler handler) {
        return new AccountMenuPermissionController(properties, handler);
    }

    @Bean
    @ConditionalOnProperty(prefix = "account.integration", name = "provider-enabled", havingValue = "true", matchIfMissing = true)
    public AccountIntegrationExceptionHandler accountIntegrationExceptionHandler() {
        return new AccountIntegrationExceptionHandler();
    }

    @Bean
    @ConditionalOnProperty(prefix = "account.integration", name = "provider-enabled", havingValue = "true", matchIfMissing = true)
    public AccountNonceStore accountNonceStore(
            @Qualifier("accountIntegrationClock") Clock accountIntegrationClock) {
        return new AccountNonceStore(accountIntegrationClock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "account.integration", name = "provider-enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<AccountIntegrationSignatureFilter> accountIntegrationSignatureFilter(
            AccountIntegrationProperties properties,
            AccountHmacSigner signer,
            AccountNonceStore nonceStore,
            ObjectMapper objectMapper,
            @Qualifier("accountIntegrationClock") Clock accountIntegrationClock) {
        FilterRegistrationBean<AccountIntegrationSignatureFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AccountIntegrationSignatureFilter(
                properties, signer, nonceStore, objectMapper, accountIntegrationClock));
        registration.addUrlPatterns(
                "/account-integration/users/*",
                "/account-integration/v1/menu-permissions",
                "/account-integration/v1/menu-permissions/*");
        registration.setOrder(1);
        return registration;
    }
}
