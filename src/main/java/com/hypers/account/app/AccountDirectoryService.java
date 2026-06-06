package com.hypers.account.app;

public class AccountDirectoryService {

    private final AccountStore store;
    private final ApplicationUserSyncClient syncClient;

    public AccountDirectoryService(AccountStore store, ApplicationUserSyncClient syncClient) {
        this.store = store;
        this.syncClient = syncClient;
    }

    public AccountApplication registerApplication(RegisterApplicationCommand command) {
        return store.saveApplication(command);
    }

    public AccountUser createUser(SaveUserCommand command) {
        return store.saveNewUser(command);
    }

    public AccountUser getUser(String userId) {
        return store.requireUser(userId);
    }

    public AccountApplication getApplication(String appCode) {
        return store.requireApplication(appCode);
    }

    public boolean isAuthorized(String userId, String appCode) {
        return store.isAuthorized(userId, appCode);
    }

    public AccountUser updateUser(String userId, SaveUserCommand command) {
        AccountUser user = store.updateUser(userId, command);
        for (AccountApplication application : store.findAuthorizedApplications(userId)) {
            syncClient.upsert(application, user);
        }
        return user;
    }

    public void authorize(String userId, String appCode) {
        AccountUser user = store.requireUser(userId);
        AccountApplication application = store.requireApplication(appCode);
        store.authorize(userId, appCode);
        syncClient.upsert(application, user);
    }

    public void deauthorize(String userId, String appCode) {
        AccountUser user = store.requireUser(userId);
        AccountApplication application = store.requireApplication(appCode);
        store.deauthorize(userId, appCode);
        syncClient.disable(application, user);
    }
}
