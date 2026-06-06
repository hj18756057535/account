package com.hypers.account.app;

public class NoopApplicationUserSyncClient implements ApplicationUserSyncClient {

    @Override
    public void upsert(AccountApplication application, AccountUser user) {
    }

    @Override
    public void disable(AccountApplication application, AccountUser user) {
    }
}
