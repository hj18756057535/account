package com.hypers.account.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class InMemoryAccountStore implements AccountStore {

    private final Map<String, AccountUser> usersById = new HashMap<>();
    private final Map<String, AccountApplication> applicationsByCode = new HashMap<>();
    private final Map<String, Set<String>> authorizedAppsByUserId = new HashMap<>();

    @Override
    public AccountUser saveNewUser(SaveUserCommand command) {
        AccountUser user = new AccountUser(
                UUID.randomUUID().toString(),
                command.getAccount(),
                command.getEmail(),
                command.getName(),
                command.getPhone());
        usersById.put(user.getId(), user);
        return user;
    }

    @Override
    public AccountUser updateUser(String userId, SaveUserCommand command) {
        AccountUser user = new AccountUser(userId, command.getAccount(), command.getEmail(), command.getName(), command.getPhone());
        usersById.put(userId, user);
        return user;
    }

    @Override
    public AccountApplication saveApplication(RegisterApplicationCommand command) {
        AccountApplication application = new AccountApplication(
                command.getAppCode(),
                command.getName(),
                command.getEntryUrl(),
                command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(),
                command.getNotifyBaseUrl(),
                command.getSecret(),
                command.getDefaultTenantCode());
        applicationsByCode.put(application.getAppCode(), application);
        return application;
    }

    @Override
    public AccountUser requireUser(String userId) {
        return Optional.ofNullable(usersById.get(userId))
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
    }

    @Override
    public AccountApplication requireApplication(String appCode) {
        return Optional.ofNullable(applicationsByCode.get(appCode))
                .orElseThrow(() -> new IllegalArgumentException("application not found"));
    }

    @Override
    public void authorize(String userId, String appCode) {
        authorizedAppsByUserId.computeIfAbsent(userId, ignored -> new HashSet<>()).add(appCode);
    }

    @Override
    public void deauthorize(String userId, String appCode) {
        authorizedAppsByUserId.computeIfAbsent(userId, ignored -> new HashSet<>()).remove(appCode);
    }

    @Override
    public List<AccountApplication> findAuthorizedApplications(String userId) {
        List<AccountApplication> applications = new ArrayList<>();
        for (String appCode : authorizedAppsByUserId.computeIfAbsent(userId, ignored -> new HashSet<>())) {
            applications.add(requireApplication(appCode));
        }
        return applications;
    }

    @Override
    public boolean isAuthorized(String userId, String appCode) {
        return authorizedAppsByUserId.computeIfAbsent(userId, ignored -> new HashSet<>()).contains(appCode);
    }
}
