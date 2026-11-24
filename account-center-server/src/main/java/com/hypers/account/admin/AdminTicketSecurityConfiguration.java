package com.hypers.account.admin;

import com.hypers.account.mapper.AdminTicketMapper;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AdminTicketSecurityConfiguration {

    @Bean
    @Primary
    public AdminTicketService safeAdminTicketService(
            AdminTicketMapper ticketMapper,
            Clock clock) {
        return new SafeAdminTicketService(ticketMapper, clock);
    }
}
