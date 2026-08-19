package com.hypers.account.app;

import java.util.List;
import java.util.UUID;

/**
 * 用户与应用管理核心服务。
 * 编排用户 CRUD、应用 CRUD、授权管理和同步通知逻辑。
 */
public class AccountDirectoryService {

    private final AccountStore store;
    private final ApplicationUserSyncClient syncClient;
    private final ApplicationUrlValidator urlValidator;

    public AccountDirectoryService(AccountStore store, ApplicationUserSyncClient syncClient) {
        this.store = store;
        this.syncClient = syncClient;
        this.urlValidator = new ApplicationUrlValidator();
    }

    public AccountApplication registerApplication(RegisterApplicationCommand command) {
        urlValidator.validate(command);
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
        if ("disabled".equals(application.getStatus())) {
            throw new IllegalArgumentException("application is disabled");
        }
        store.authorize(userId, appCode);
        syncClient.upsert(application, user);
    }

    public void deauthorize(String userId, String appCode) {
        AccountUser user = store.requireUser(userId);
        AccountApplication application = store.requireApplication(appCode);
        store.deauthorize(userId, appCode);
        syncClient.disable(application, user);
    }

    public List<AccountUser> findUsers(String keyword, String status) {
        return store.findUsers(keyword, status);
    }

    public List<AccountApplication> findApplications(String keyword, String status) {
        return store.findApplications(keyword, status);
    }

    public List<AccountApplication> getUserAuthorizedApplications(String userId) {
        store.requireUser(userId);
        return store.findAuthorizedApplications(userId);
    }

    public AccountUser updateUserFields(String userId, SaveUserCommand command) {
        return store.updateUser(userId, command);
    }

    public void enableUser(String userId) {
        store.requireUser(userId);
        store.updateUserStatus(userId, "enabled");
    }

    public void disableUser(String userId) {
        AccountUser user = store.requireUser(userId);
        store.updateUserStatus(userId, "disabled");
        for (AccountApplication app : store.findAuthorizedApplications(userId)) {
            syncClient.disable(app, user);
        }
    }

    public AccountApplication updateApplication(String appCode, RegisterApplicationCommand command) {
        store.requireApplication(appCode);
        urlValidator.validate(command);
        return store.saveApplication(command);
    }

    public void enableApplication(String appCode) {
        store.requireApplication(appCode);
        store.updateApplicationStatus(appCode, "enabled");
    }

    public void disableApplication(String appCode) {
        store.requireApplication(appCode);
        store.updateApplicationStatus(appCode, "disabled");
    }

    public String rotateApplicationSecret(String appCode) {
        AccountApplication app = store.requireApplication(appCode);
        String newSecret = UUID.randomUUID().toString().replace("-", "");
        int newVersion = (app.getSecretVersion() != null ? app.getSecretVersion() : 0) + 1;
        store.rotateApplicationSecret(appCode, newSecret, newVersion);
        return newSecret;
    }
}
