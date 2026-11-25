package com.hypers.account.config;

import com.hypers.account.admin.AdminTicketService;
import com.hypers.account.auth.AccountLoginService;
import com.hypers.account.auth.AdminAuthorizationService;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.mapper.AccountUserMapper;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.mapper.AdminTicketMapper;
import com.hypers.account.mapper.AuditLogMapper;
import com.hypers.account.security.HmacSignatureService;
import com.hypers.account.sso.SsoTicketService;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Account Center 核心配置。
 *
 * <p>只装配无状态组件和需要显式参数的 Bean；业务服务统一使用 {@code @Service} 注解，
 * 持久化由 MyBatis Mapper 直接提供。
 */
@Configuration
public class AccountCenterConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public HmacSignatureService hmacSignatureService() {
        return new HmacSignatureService();
    }

    @Bean
    public AccountLoginService accountLoginService(AccountUserMapper userMapper) {
        return new AccountLoginService(userMapper);
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
