package com.hypers.account.starter.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccountNonceStore {

    private final Map<String, Instant> expiresAtByNonce = new ConcurrentHashMap<>();
    private final Clock clock;

    public boolean markIfAbsent(String appCode, String nonce, Instant expiresAt) {
        expiresAtByNonce.entrySet().removeIf(entry -> !entry.getValue().isAfter(clock.instant()));
        return expiresAtByNonce.putIfAbsent(appCode + ':' + nonce, expiresAt) == null;
    }
}
