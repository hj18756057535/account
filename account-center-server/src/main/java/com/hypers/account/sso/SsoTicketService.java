package com.hypers.account.sso;

import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;

public class SsoTicketService {

    private final Clock clock;
    private final Duration ttl;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public SsoTicketService(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public String issue(String appCode, AccountUserSnapshot user) {
        String code = UUID.randomUUID().toString().replace("-", "");
        tickets.put(code, new Ticket(appCode, user, clock.instant().plus(ttl)));
        return code;
    }

    public SsoUserPayload exchange(String appCode, String code) {
        Ticket ticket = tickets.get(code);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.TICKET_INVALID);
        }
        if (ticket.used.get()) {
            throw new BusinessException(ErrorCode.TICKET_INVALID);
        }
        if (!ticket.appCode.equals(appCode)) {
            throw new BusinessException(ErrorCode.TICKET_INVALID);
        }
        if (!ticket.expiresAt.isAfter(clock.instant())) {
            throw new BusinessException(ErrorCode.TICKET_INVALID);
        }
        if (!ticket.used.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.TICKET_INVALID);
        }
        AccountUserSnapshot user = ticket.user;
        return new SsoUserPayload(
                user.getExternalUserId(),
                user.getAccount(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getTenantCode());
    }

    @RequiredArgsConstructor
    private static class Ticket {

        private final String appCode;
        private final AccountUserSnapshot user;
        private final Instant expiresAt;
        private final AtomicBoolean used = new AtomicBoolean();
    }
}
