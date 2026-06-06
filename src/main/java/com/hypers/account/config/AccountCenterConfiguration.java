package com.hypers.account.config;

import com.hypers.account.admin.AdminTicketService;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountStore;
import com.hypers.account.app.ApplicationUserSyncClient;
import com.hypers.account.app.InMemoryAccountStore;
import com.hypers.account.app.MyBatisAccountStore;
import com.hypers.account.app.NoopApplicationUserSyncClient;
import com.hypers.account.app.http.HttpApplicationUserSyncClient;
import com.hypers.account.auth.AccountLoginService;
import com.hypers.account.auth.AdminAuthorizationService;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.mapper.AccountApplicationMapper;
import com.hypers.account.mapper.AccountUserApplicationMapper;
import com.hypers.account.mapper.AccountUserMapper;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.mapper.AdminTicketMapper;
import com.hypers.account.mapper.AuditLogMapper;
import com.hypers.account.security.HmacSignatureService;
import com.hypers.account.sso.SsoTicketService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Account Center 核心配置。
 * 管理 Store 实现切换、同步客户端选择和 SSO 参数。
 */
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
    @ConditionalOnProperty(name = "account.store.type", havingValue = "mybatis", matchIfMissing = true)
    public AccountStore myBatisAccountStore(AccountUserMapper userMapper,
                                            AccountApplicationMapper applicationMapper,
                                            AccountUserApplicationMapper userApplicationMapper) {
        return new MyBatisAccountStore(userMapper, applicationMapper, userApplicationMapper);
    }

    @Bean
    public HmacSignatureService hmacSignatureService() {
        return new HmacSignatureService();
    }

    /** 同步客户端：默认使用 HTTP 真实调用，可通过 account.sync.type=noop 切换为空实现 */
    @Bean
    @ConditionalOnProperty(name = "account.sync.type", havingValue = "noop")
    public ApplicationUserSyncClient noopSyncClient() {
        return new NoopApplicationUserSyncClient();
    }

    @Bean
    @ConditionalOnProperty(name = "account.sync.type", havingValue = "http", matchIfMissing = true)
    public ApplicationUserSyncClient httpSyncClient(HmacSignatureService signatureService) {
        return new HttpApplicationUserSyncClient(signatureService);
    }

    @Bean
    public AccountDirectoryService accountDirectoryService(
            AccountStore store,
            ApplicationUserSyncClient syncClient) {
        return new AccountDirectoryService(store, syncClient);
    }

    @Bean
    public AccountLoginService accountLoginService(AccountStore store) {
        return new AccountLoginService(store);
    }

    @Bean
    public AdminTicketService adminTicketService(AdminTicketMapper ticketMapper, Clock clock) {
        return new AdminTicketService(ticketMapper, clock);
    }

    @Bean
    public AuditLogService auditLogService(AuditLogMapper auditLogMapper) {
        return new AuditLogService(auditLogMapper);
    }

    @Bean
    public AdminAuthorizationService adminAuthorizationService(AdminRoleMapper adminRoleMapper) {
        return new AdminAuthorizationService(adminRoleMapper);
    }

    @Bean
    public SsoTicketService ssoTicketService(Clock clock) {
        return new SsoTicketService(clock, Duration.ofSeconds(60));
    }
}
