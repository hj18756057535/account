package com.hypers.account.admin;

import com.hypers.account.mapper.AdminTicketMapper;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class SafeAdminTicketService extends AdminTicketService {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final AdminTicketMapper ticketMapper;
    private final Clock clock;

    public SafeAdminTicketService(AdminTicketMapper ticketMapper, Clock clock) {
        super(ticketMapper, clock);
        this.ticketMapper = ticketMapper;
        this.clock = clock;
    }

    @Override
    public AdminTicketPayload verify(String appCode, String ticket) {
        // 票据过期列沿用签发时的 UTC 文本口径，避免 JVM 时区改变比较结果。
        int updated = ticketMapper.consume(ticket, appCode, FORMATTER.format(clock.instant()));
        if (updated != 1) {
            throw new IllegalArgumentException("ticket 无效或已过期");
        }
        String userId = ticketMapper.selectUserIdByCode(ticket);
        return new AdminTicketPayload(appCode, userId);
    }
}
