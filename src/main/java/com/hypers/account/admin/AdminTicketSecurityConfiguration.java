package com.hypers.account.admin;

import com.hypers.account.mapper.AdminTicketMapper;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AdminTicketSecurityConfiguration {

    @Bean
    @Primary
    public AdminTicketService safeAdminTicketService(
            AdminTicketMapper ticketMapper,
            Clock clock,
            JdbcTemplate jdbcTemplate) {
        return new SafeAdminTicketService(ticketMapper, clock, jdbcTemplate);
    }
}
