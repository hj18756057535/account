package com.hypers.account.integration.menu;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAccountNonceStore implements AccountNonceStore {

    private final Map<String, Long> expirations = new ConcurrentHashMap<String, Long>();

    @Override
    public boolean markIfAbsent(String appCode, String nonce, long expiresAtEpochMillis) {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : expirations.entrySet()) {
            if (entry.getValue().longValue() <= now) {
                expirations.remove(entry.getKey(), entry.getValue());
            }
        }
        return expirations.putIfAbsent(appCode + ':' + nonce, Long.valueOf(expiresAtEpochMillis)) == null;
    }
}
