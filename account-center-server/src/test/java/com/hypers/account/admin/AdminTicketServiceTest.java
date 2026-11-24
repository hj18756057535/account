package com.hypers.account.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * AdminTicketService 闂嗗棙鍨氬ù瀣槸閵? * 娴ｈ法鏁?@SpringBootTest 閸氼垰濮╃€瑰本鏆ｆ稉濠佺瑓閺傚浄绱滰Transactional 濮ｅ繑顐煎ù瀣槸閸氬骸娲栧姘モ偓? */
@SpringBootTest
@Transactional
class AdminTicketServiceTest {

    @Autowired
    private AdminTicketService ticketService;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void verifyReturnsPayloadForValidTicket() {
        String code = ticketService.issue("cms-ai", "user-1", "iframe-permission");

        AdminTicketPayload payload = ticketService.verify("cms-ai", code);

        assertThat(payload.getAppCode()).isEqualTo("cms-ai");
        assertThat(payload.getUserId()).isEqualTo("user-1");
    }

    @Test
    void verifyRejectsAlreadyUsedTicket() {
        String code = ticketService.issue("cms-ai", "user-1", "iframe-permission");
        ticketService.verify("cms-ai", code);

        assertThatThrownBy(() -> ticketService.verify("cms-ai", code))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyRejectsApplicationMismatch() {
        String code = ticketService.issue("cms-ai", "user-1", "iframe-permission");

        assertThatThrownBy(() -> ticketService.verify("other-app", code))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyRejectsExpiredTicket() {
        String code = ticketService.issue("cms-ai", "user-1", "iframe-permission");
        jdbc.update("update account_admin_tickets set expires_at = '2000-01-01 00:00:00' where code = ?", code);
        assertThatThrownBy(() -> ticketService.verify("cms-ai", code)).isInstanceOf(IllegalArgumentException.class);
    }
}
