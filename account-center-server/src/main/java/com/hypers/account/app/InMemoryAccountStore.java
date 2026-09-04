package com.hypers.account.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class InMemoryAccountStore implements AccountStore {

    private final Map<String, AccountUser> usersById = new HashMap<>();
    private final Map<String, AccountApplication> applicationsByCode = new HashMap<>();
    private final Map<String, Set<String>> authorizedAppsByUserId = new HashMap<>();
    private final Map<String, ApplicationAccess> applicationAccessByKey = new HashMap<>();
    private final Map<String, ApplicationSyncCommand> syncCommandsById = new HashMap<>();

    @Override
    public AccountUser saveNewUser(SaveUserCommand command) {
        return saveNewUser(command, null);
    }

    @Override
    public AccountUser saveNewUser(SaveUserCommand command, String operatorId) {
        ensureAccountAvailable(command.getAccount(), null);
        AccountUser user = new AccountUser(
                UUID.randomUUID().toString().replace("-", ""),
                command.getAccount(),
                command.getEmail(),
                command.getName(),
                command.getPhone());
        user.setCreatedBy(operatorId);
        usersById.put(user.getId(), user);
        return user;
    }

    @Override
    public AccountUser updateUser(String userId, SaveUserCommand command) {
        AccountUser old = requireUser(userId);
        ensureAccountAvailable(command.getAccount(), userId);
        AccountUser user = new AccountUser(userId, command.getAccount(), command.getEmail(), command.getName(), command.getPhone(),
                old.getStatus(), old.getVersion() + 1, old.getCreatedBy(), old.getUpdatedBy(), old.getCreatedAt(), old.getUpdatedAt());
        usersById.put(userId, user);
        return user;
    }

    @Override
    public AccountUser updateUser(String userId,
                                  SaveUserCommand command,
                                  long expectedVersion,
                                  String operatorId) {
        AccountUser old = requireUser(userId);
        if (old.getVersion() != expectedVersion) {
            throw new ResourceVersionConflictException();
        }
        ensureAccountAvailable(command.getAccount(), userId);
        AccountUser user = new AccountUser(userId, command.getAccount(), command.getEmail(), command.getName(), command.getPhone(),
                old.getStatus(), old.getVersion() + 1, old.getCreatedBy(), operatorId, old.getCreatedAt(), old.getUpdatedAt());
        user.setPassword(old.getPassword());
        usersById.put(userId, user);
        return user;
    }

    @Override
    public AccountApplication saveApplication(RegisterApplicationCommand command) {
        String defaultTenantCode = Optional.ofNullable(command.getDefaultTenantCode())
                .filter(value -> !value.trim().isEmpty())
                .orElse("default");
        AccountApplication application = new AccountApplication(
                command.getAppCode(),
                command.getName(),
                command.getEntryUrl(),
                command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(),
                command.getNotifyBaseUrl(),
                command.getSecret(),
                defaultTenantCode);
        applicationsByCode.put(application.getAppCode(), application);
        return application;
    }

    @Override
    public AccountApplication createManagedApplication(SaveApplicationCommand command, String operatorId) {
        if (applicationsByCode.containsKey(command.getAppCode())) {
            throw new ApplicationAlreadyExistsException();
        }
        AccountApplication application = toManagedApplication(command, operatorId);
        applicationsByCode.put(application.getAppCode(), application);
        return application;
    }

    @Override
    public AccountApplication updateManagedApplication(SaveApplicationCommand command, String operatorId) {
        AccountApplication application = requireApplication(command.getAppCode());
        if (application.getVersion() != command.getExpectedVersion()) {
            throw new ResourceVersionConflictException();
        }
        application.setName(command.getName());
        application.setEntryUrl(command.getEntryUrl());
        application.setSsoCallbackUrl(command.getSsoCallbackUrl());
        application.setPermissionIframeUrl(command.getPermissionIframeUrl());
        application.setNotifyBaseUrl(command.getNotifyBaseUrl());
        application.setDefaultTenantCode(normalizeTenant(command.getDefaultTenantCode()));
        application.setProtocolCapabilities(command.getProtocolCapabilities());
        application.setUpdatedBy(operatorId);
        application.setVersion(application.getVersion() + 1);
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
    public List<ApplicationAccess> findApplicationAccess(String userId) {
        return applicationAccessByKey.values().stream()
                .filter(access -> userId.equals(access.getUserId()))
                .sorted(Comparator.comparing(ApplicationAccess::getAppCode))
                .collect(Collectors.toList());
    }

    @Override
    public ApplicationAccess requireApplicationAccess(String userId, String appCode) {
        return Optional.ofNullable(applicationAccessByKey.get(accessKey(userId, appCode)))
                .orElseThrow(() -> new IllegalArgumentException("application access not found"));
    }

    @Override
    public ApplicationAccess saveApplicationAccess(String userId,
                                                   String appCode,
                                                   String desiredStatus,
                                                   long expectedVersion,
                                                   String operatorId) {
        String key = accessKey(userId, appCode);
        ApplicationAccess existing = applicationAccessByKey.get(key);
        if ((existing == null && expectedVersion != 0)
                || (existing != null && existing.getVersion() != expectedVersion)) {
            throw new ResourceVersionConflictException();
        }
        long version = existing == null ? 1 : existing.getVersion() + 1;
        ApplicationAccess access = new ApplicationAccess(
                userId, appCode, desiredStatus, version,
                "pending_application_adaptation", null, java.time.Instant.now());
        applicationAccessByKey.put(key, access);
        if ("enabled".equals(desiredStatus)) {
            authorizedAppsByUserId.computeIfAbsent(userId, ignored -> new HashSet<>()).add(appCode);
        } else {
            authorizedAppsByUserId.computeIfAbsent(userId, ignored -> new HashSet<>()).remove(appCode);
        }
        return access;
    }

    @Override
    public void saveSyncCommand(ApplicationSyncCommand command) {
        syncCommandsById.put(command.getId(), command);
    }

    @Override
    public boolean isAuthorized(String userId, String appCode) {
        return authorizedAppsByUserId.computeIfAbsent(userId, ignored -> new HashSet<>()).contains(appCode);
    }

    @Override
    public List<AccountUser> findUsers(String keyword, String status) {
        return usersById.values().stream()
                .filter(u -> status == null || status.isEmpty() || status.equals(u.getStatus()))
                .filter(u -> keyword == null || keyword.isEmpty()
                        || u.getAccount().contains(keyword)
                        || u.getName().contains(keyword)
                        || u.getEmail().contains(keyword)
                        || u.getPhone().contains(keyword))
                .collect(Collectors.toList());
    }

    @Override
    public PageResult<AccountUser> findUsersPage(UserPageQuery query) {
        List<AccountUser> matches = findUsers(query.getKeyword(), query.getStatus());
        matches.sort(userComparator(query));
        int fromIndex = Math.min(query.getOffset(), matches.size());
        int toIndex = Math.min(fromIndex + query.getSize(), matches.size());
        return new PageResult<>(
                new ArrayList<>(matches.subList(fromIndex, toIndex)),
                query.getPage(),
                query.getSize(),
                matches.size());
    }

    private Comparator<AccountUser> userComparator(UserPageQuery query) {
        Comparator<AccountUser> comparator;
        boolean descending = "desc".equals(query.getSortDirection());
        if ("account".equals(query.getSortField())) {
            Comparator<String> valueComparator = descending
                    ? String.CASE_INSENSITIVE_ORDER.reversed()
                    : String.CASE_INSENSITIVE_ORDER;
            comparator = Comparator.comparing(AccountUser::getAccount, Comparator.nullsLast(valueComparator));
        } else if ("name".equals(query.getSortField())) {
            Comparator<String> valueComparator = descending
                    ? String.CASE_INSENSITIVE_ORDER.reversed()
                    : String.CASE_INSENSITIVE_ORDER;
            comparator = Comparator.comparing(AccountUser::getName, Comparator.nullsLast(valueComparator));
        } else {
            Comparator<java.time.Instant> valueComparator = descending
                    ? Comparator.reverseOrder()
                    : Comparator.naturalOrder();
            comparator = Comparator.comparing(AccountUser::getCreatedAt, Comparator.nullsLast(valueComparator));
        }
        return comparator.thenComparing(AccountUser::getId);
    }

    @Override
    public List<AccountApplication> findApplications(String keyword, String status) {
        return applicationsByCode.values().stream()
                .filter(a -> status == null || status.isEmpty() || status.equals(a.getStatus()))
                .filter(a -> keyword == null || keyword.isEmpty()
                        || a.getAppCode().contains(keyword)
                        || a.getName().contains(keyword))
                .collect(Collectors.toList());
    }

    @Override
    public void updateUserStatus(String userId, String status) {
        AccountUser user = requireUser(userId);
        user.setStatus(status);
        user.setVersion(user.getVersion() + 1);
    }

    @Override
    public AccountUser updateUserStatus(String userId,
                                        String status,
                                        long expectedVersion,
                                        String operatorId) {
        AccountUser user = requireUser(userId);
        if (user.getVersion() != expectedVersion) {
            throw new ResourceVersionConflictException();
        }
        user.setStatus(status);
        user.setUpdatedBy(operatorId);
        user.setVersion(user.getVersion() + 1);
        return user;
    }

    @Override
    public void updateApplicationStatus(String appCode, String status) {
        AccountApplication app = requireApplication(appCode);
        app.setStatus(status);
    }

    @Override
    public AccountApplication updateManagedApplicationStatus(String appCode,
                                                             String status,
                                                             long expectedVersion,
                                                             String operatorId) {
        AccountApplication application = requireApplication(appCode);
        if (application.getVersion() != expectedVersion) {
            throw new ResourceVersionConflictException();
        }
        application.setStatus(status);
        application.setUpdatedBy(operatorId);
        application.setVersion(application.getVersion() + 1);
        return application;
    }

    @Override
    public void rotateApplicationSecret(String appCode, String newSecret, int newVersion) {
        AccountApplication app = requireApplication(appCode);
        app.setSecret(newSecret);
        app.setSecretVersion(newVersion);
    }

    @Override
    public AccountApplication rotateManagedApplicationSecret(String appCode,
                                                              String newSecret,
                                                              int newSecretVersion,
                                                              long expectedVersion,
                                                              String operatorId) {
        AccountApplication application = requireApplication(appCode);
        if (application.getVersion() != expectedVersion) {
            throw new ResourceVersionConflictException();
        }
        application.setSecret(newSecret);
        application.setSecretVersion(newSecretVersion);
        application.setSecretState("active");
        application.setUpdatedBy(operatorId);
        application.setVersion(application.getVersion() + 1);
        return application;
    }

    @Override
    public AccountApplication revokeManagedApplicationSecret(String appCode,
                                                              long expectedVersion,
                                                              String operatorId) {
        AccountApplication application = requireApplication(appCode);
        if (application.getVersion() != expectedVersion) {
            throw new ResourceVersionConflictException();
        }
        application.setSecretState("revoked");
        application.setUpdatedBy(operatorId);
        application.setVersion(application.getVersion() + 1);
        return application;
    }

    @Override
    public AccountUser findUserByAccount(String account) {
        return usersById.values().stream()
                .filter(u -> u.getAccount().equals(account))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void setUserPassword(String userId, String encodedPassword) {
        AccountUser user = requireUser(userId);
        user.setPassword(encodedPassword);
    }

    private void ensureAccountAvailable(String account, String currentUserId) {
        AccountUser existing = findUserByAccount(account);
        if (existing != null && !existing.getId().equals(currentUserId)) {
            throw new AccountAlreadyExistsException();
        }
    }

    private AccountApplication toManagedApplication(SaveApplicationCommand command, String operatorId) {
        AccountApplication application = new AccountApplication(
                command.getAppCode(), command.getName(), command.getEntryUrl(), command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(), command.getNotifyBaseUrl(), command.getSecret(),
                normalizeTenant(command.getDefaultTenantCode()));
        application.setProtocolCapabilities(command.getProtocolCapabilities());
        application.setCreatedBy(operatorId);
        return application;
    }

    private String normalizeTenant(String tenantCode) {
        return Optional.ofNullable(tenantCode)
                .filter(value -> !value.trim().isEmpty())
                .map(String::trim)
                .orElse("default");
    }

    private String accessKey(String userId, String appCode) {
        return userId + "\n" + appCode;
    }
}
