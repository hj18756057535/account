package com.hypers.account.app;

public interface ApplicationUserSyncClient {

    void upsert(AccountApplication application, AccountUser user);

    void disable(AccountApplication application, AccountUser user);
}
