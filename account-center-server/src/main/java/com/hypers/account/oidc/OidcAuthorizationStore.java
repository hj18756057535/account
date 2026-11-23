package com.hypers.account.oidc;

public interface OidcAuthorizationStore {
    OidcAuthorizationRecord findById(String id);
    OidcAuthorizationRecord findByTokenHash(String hash, String type);
    void save(OidcAuthorizationRecord record);
    void remove(String id);
}
