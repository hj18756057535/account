package com.hypers.account.sso;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
            throw new SsoTicketException("ticket not found");
        }
        if (ticket.used) {
            throw new SsoTicketException("ticket already used");
        }
        if (!ticket.appCode.equals(appCode)) {
            throw new SsoTicketException("ticket application mismatch");
        }
        if (ticket.expiresAt.isBefore(clock.instant()) || ticket.expiresAt.equals(clock.instant())) {
            throw new SsoTicketException("ticket expired");
        }
        ticket.used = true;
        AccountUserSnapshot user = ticket.user;
        return new SsoUserPayload(
                user.getExternalUserId(),
                user.getAccount(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getTenantCode());
    }

    private static class Ticket {

        private final String appCode;
        private final AccountUserSnapshot user;
        private final Instant expiresAt;
        private boolean used;

        private Ticket(String appCode, AccountUserSnapshot user, Instant expiresAt) {
            this.appCode = appCode;
            this.user = user;
            this.expiresAt = expiresAt;
        }
    }
}
