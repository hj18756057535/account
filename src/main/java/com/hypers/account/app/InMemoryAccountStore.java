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
        AccountUser old = usersById.get(userId);
        String status = old != null ? old.getStatus() : "enabled";
        AccountUser user = new AccountUser(userId, command.getAccount(), command.getEmail(), command.getName(), command.getPhone(),
                status, null, null, null, null);
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
    }

    @Override
    public void updateApplicationStatus(String appCode, String status) {
        AccountApplication app = requireApplication(appCode);
        app.setStatus(status);
    }

    @Override
    public void rotateApplicationSecret(String appCode, String newSecret, int newVersion) {
        AccountApplication app = requireApplication(appCode);
        app.setSecret(newSecret);
        app.setSecretVersion(newVersion);
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
}
