package com.hypers.account.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryNonceStore {

    private final Map<String, Instant> expiresAtByKey = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryNonceStore(Clock clock) {
        this.clock = clock;
    }

    public boolean markIfAbsent(String appCode, String nonce, String purpose, Instant expiresAt) {
        cleanupExpired();
        String key = appCode + ":" + purpose + ":" + nonce;
        return expiresAtByKey.putIfAbsent(key, expiresAt) == null;
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, Instant>> iterator = expiresAtByKey.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }
}
