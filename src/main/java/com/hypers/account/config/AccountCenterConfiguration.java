package com.hypers.account.config;

import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountStore;
import com.hypers.account.app.ApplicationUserSyncClient;
import com.hypers.account.app.InMemoryAccountStore;
import com.hypers.account.app.JdbcAccountStore;
import com.hypers.account.app.NoopApplicationUserSyncClient;
import com.hypers.account.sso.SsoTicketService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AccountCenterConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "account.store.type", havingValue = "memory")
    public AccountStore inMemoryAccountStore() {
        return new InMemoryAccountStore();
    }

    @Bean
    @ConditionalOnProperty(name = "account.store.type", havingValue = "jdbc", matchIfMissing = true)
    public AccountStore jdbcAccountStore(JdbcTemplate jdbcTemplate) {
        return new JdbcAccountStore(jdbcTemplate);
    }

    @Bean
    public ApplicationUserSyncClient applicationUserSyncClient() {
        return new NoopApplicationUserSyncClient();
    }

    @Bean
    public AccountDirectoryService accountDirectoryService(
            AccountStore store,
            ApplicationUserSyncClient syncClient) {
        return new AccountDirectoryService(store, syncClient);
    }

    @Bean
    public SsoTicketService ssoTicketService(Clock clock) {
        return new SsoTicketService(clock, Duration.ofSeconds(60));
    }
}
