package com.hypers.account.admin;

import com.hypers.account.mapper.AdminTicketMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.JdbcTemplate;

public class SafeAdminTicketService extends AdminTicketService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public SafeAdminTicketService(AdminTicketMapper ticketMapper, Clock clock, JdbcTemplate jdbcTemplate) {
        super(ticketMapper, clock);
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    public AdminTicketPayload verify(String appCode, String ticket) {
        int updated = jdbcTemplate.update(
                "update account_admin_tickets "
                        + "set used_at = current_timestamp "
                        + "where code = ? and app_code = ? and used_at is null and expires_at > ?",
                ticket,
                appCode,
                FORMATTER.format(clock.instant()));
        if (updated != 1) {
            throw new IllegalArgumentException("ticket 无效或已过期");
        }
        String userId = jdbcTemplate.queryForObject(
                "select user_id from account_admin_tickets where code = ? and app_code = ?",
                String.class,
                ticket,
                appCode);
        return new AdminTicketPayload(appCode, userId);
    }
}
