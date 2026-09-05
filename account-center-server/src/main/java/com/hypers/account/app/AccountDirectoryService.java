package com.hypers.account.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * 用户与应用管理核心服务。
 * 编排用户 CRUD、应用 CRUD、授权管理和同步通知逻辑。
 */
@RequiredArgsConstructor
public class AccountDirectoryService {

    private final AccountStore store;

    public List<String> findExistingAccounts(List<String> accounts) {
        return store.findExistingAccounts(accounts);
    }

    public List<AccountUser> createManagedUsers(List<SaveUserCommand> commands, String operatorId) {
        return store.saveNewUsers(commands, operatorId);
    }
    private final ApplicationUserSyncClient syncClient;
    private final ApplicationUrlValidator urlValidator = new ApplicationUrlValidator();

    public AccountApplication registerApplication(RegisterApplicationCommand command) {
        urlValidator.validate(command);
        return store.saveApplication(command);
    }

    public AccountApplication createManagedApplication(SaveApplicationCommand command, String operatorId) {
        urlValidator.validate(command);
        return store.createManagedApplication(command, operatorId);
    }

    public AccountApplication updateManagedApplication(SaveApplicationCommand command, String operatorId) {
        urlValidator.validate(command);
        return store.updateManagedApplication(command, operatorId);
    }

    public AccountUser createUser(SaveUserCommand command) {
        return store.saveNewUser(command);
    }

    public AccountUser createManagedUser(SaveUserCommand command, String operatorId) {
        return store.saveNewUser(command, operatorId);
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
            if (!application.isManagedSync()) syncClient.upsert(application, user);
        }
        return user;
    }

    public void authorize(String userId, String appCode) {
        AccountUser user = store.requireUser(userId);
        AccountApplication application = store.requireApplication(appCode);
        if (application.isManagedSync()) throw new IllegalArgumentException("managed application requires versioned access API");
        if ("disabled".equals(application.getStatus())) {
            throw new IllegalArgumentException("application is disabled");
        }
        store.authorize(userId, appCode);
        syncClient.upsert(application, user);
    }

    public void deauthorize(String userId, String appCode) {
        AccountUser user = store.requireUser(userId);
        AccountApplication application = store.requireApplication(appCode);
        if (application.isManagedSync()) throw new IllegalArgumentException("managed application requires versioned access API");
        store.deauthorize(userId, appCode);
        syncClient.disable(application, user);
    }

    public List<AccountUser> findUsers(String keyword, String status) {
        return store.findUsers(keyword, status);
    }

    public PageResult<AccountUser> findUsersPage(UserPageQuery query) {
        return store.findUsersPage(query);
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

    public List<ApplicationAccess> getUserApplicationAccess(String userId) {
        store.requireUser(userId);
        Map<String, ApplicationAccess> configured = store.findApplicationAccess(userId).stream()
                .collect(Collectors.toMap(ApplicationAccess::getAppCode, Function.identity()));
        List<ApplicationAccess> result = new ArrayList<>();
        for (AccountApplication application : store.findApplications(null, null)) {
            result.add(configured.getOrDefault(application.getAppCode(), new ApplicationAccess(
                    userId,
                    application.getAppCode(),
                    "disabled",
                    0,
                    "pending_application_adaptation",
                    null,
                    null)));
        }
        return result;
    }

    public ApplicationAccess changeUserApplicationAccess(String userId,
                                                         String appCode,
                                                         String desiredStatus,
                                                         long expectedVersion,
                                                         String operatorId,
                                                         ApplicationSyncCommand syncCommand) {
        store.requireUser(userId);
        AccountApplication application = store.requireApplication(appCode);
        if ("enabled".equals(desiredStatus) && "disabled".equals(application.getStatus())) {
            throw new IllegalStateException("application is disabled");
        }
        ApplicationAccess access = store.saveApplicationAccess(
                userId, appCode, desiredStatus, expectedVersion, operatorId);
        store.saveSyncCommand(syncCommand);
        access.setSyncCommandId(syncCommand.getId());
        return access;
    }

    public AccountUser updateManagedUser(String userId,
                                         SaveUserCommand command,
                                         long expectedVersion,
                                         String operatorId) {
        return store.updateUser(userId, command, expectedVersion, operatorId);
    }

    public AccountUser changeManagedUserStatus(String userId,
                                               String status,
                                               long expectedVersion,
                                               String operatorId) {
        return store.updateUserStatus(userId, status, expectedVersion, operatorId);
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

    public AccountApplication changeManagedApplicationStatus(String appCode,
                                                             String status,
                                                             long expectedVersion,
                                                             String operatorId) {
        return store.updateManagedApplicationStatus(appCode, status, expectedVersion, operatorId);
    }

    public String rotateApplicationSecret(String appCode) {
        AccountApplication app = store.requireApplication(appCode);
        String newSecret = UUID.randomUUID().toString().replace("-", "");
        int newVersion = (app.getSecretVersion() != null ? app.getSecretVersion() : 0) + 1;
        store.rotateApplicationSecret(appCode, newSecret, newVersion);
        return newSecret;
    }

    public AccountApplication rotateManagedApplicationSecret(String appCode,
                                                              String secret,
                                                              long expectedVersion,
                                                              String operatorId) {
        AccountApplication application = store.requireApplication(appCode);
        int nextSecretVersion = (application.getSecretVersion() == null ? 0 : application.getSecretVersion()) + 1;
        return store.rotateManagedApplicationSecret(
                appCode, secret, nextSecretVersion, expectedVersion, operatorId);
    }

    public AccountApplication revokeManagedApplicationSecret(String appCode,
                                                              long expectedVersion,
                                                              String operatorId) {
        return store.revokeManagedApplicationSecret(appCode, expectedVersion, operatorId);
    }
}
