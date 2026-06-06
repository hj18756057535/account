package com.hypers.account.admin;

import com.hypers.account.mapper.AdminTicketMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 管理端 ticket 服务。
 * 用于 iframe 授权页面的短期凭证：签发 60 秒 TTL，验证时校验存在、未过期、匹配应用、未使用。
 */
public class AdminTicketService {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final AdminTicketMapper ticketMapper;
    private final Clock clock;

    public AdminTicketService(AdminTicketMapper ticketMapper, Clock clock) {
        this.ticketMapper = ticketMapper;
        this.clock = clock;
    }

    /**
     * 签发 admin ticket。
     * 60 秒有效期，一次性使用，用于 iframe 授权页面的身份凭证。
     */
    public String issue(String appCode, String userId, String purpose) {
        String code = UUID.randomUUID().toString().replace("-", "");
        Instant expiresAt = clock.instant().plus(TTL);
        ticketMapper.insert(code, appCode, userId, purpose, FORMATTER.format(expiresAt));
        return code;
    }

    /**
     * 验证 admin ticket。
     * 校验存在、匹配应用、未过期、未使用，通过后标记已使用并返回 payload。
     * 验证失败抛出 IllegalArgumentException。
     */
    public AdminTicketPayload verify(String appCode, String ticket) {
        // 检查 ticket 是否有效（存在、未过期、匹配应用编码）
        Integer valid = ticketMapper.countValid(ticket, appCode, FORMATTER.format(clock.instant()));
        if (valid == null || valid == 0) {
            throw new IllegalArgumentException("ticket 无效或已过期");
        }
        // 检查是否已被使用（一次性凭证）
        Integer used = ticketMapper.countUsed(ticket);
        if (used != null && used > 0) {
            throw new IllegalArgumentException("ticket 已被使用");
        }
        // 标记已使用
        ticketMapper.markUsed(ticket);
        // 获取关联的用户 ID
        String userId = ticketMapper.selectUserIdByCode(ticket);
        return new AdminTicketPayload(appCode, userId);
    }
}
