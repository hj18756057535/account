package com.hypers.account.integration.menu;

public interface AccountNonceStore {

    boolean markIfAbsent(String appCode, String nonce, long expiresAtEpochMillis);
}
