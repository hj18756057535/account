package com.hypers.account.app;

import com.hypers.account.mapper.AccountApplicationMapper;
import com.hypers.account.mapper.AccountUserApplicationMapper;
import com.hypers.account.mapper.AccountUserMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MyBatisAccountStore implements AccountStore {

    private final AccountUserMapper userMapper;
    private final AccountApplicationMapper applicationMapper;
    private final AccountUserApplicationMapper userApplicationMapper;

    public MyBatisAccountStore(AccountUserMapper userMapper,
                               AccountApplicationMapper applicationMapper,
                               AccountUserApplicationMapper userApplicationMapper) {
        this.userMapper = userMapper;
        this.applicationMapper = applicationMapper;
        this.userApplicationMapper = userApplicationMapper;
    }

    @Override
    public AccountUser saveNewUser(SaveUserCommand command) {
        AccountUser user = new AccountUser(
                UUID.randomUUID().toString(),
                command.getAccount(),
                command.getEmail(),
                command.getName(),
                command.getPhone());
        userMapper.insert(user);
        return user;
    }

    @Override
    public AccountUser updateUser(String userId, SaveUserCommand command) {
        AccountUser user = requireUser(userId);
        user.setAccount(command.getAccount());
        user.setEmail(command.getEmail());
        user.setName(command.getName());
        user.setPhone(command.getPhone());
        userMapper.update(user);
        return requireUser(userId);
    }

    @Override
    public AccountApplication saveApplication(RegisterApplicationCommand command) {
        String defaultTenantCode = Optional.ofNullable(command.getDefaultTenantCode())
                .filter(value -> !value.trim().isEmpty())
                .orElse("default");
        AccountApplication existing = applicationMapper.selectByAppCode(command.getAppCode());
        if (existing != null) {
            existing.setName(command.getName());
            existing.setEntryUrl(command.getEntryUrl());
            existing.setSsoCallbackUrl(command.getSsoCallbackUrl());
            existing.setPermissionIframeUrl(command.getPermissionIframeUrl());
            existing.setNotifyBaseUrl(command.getNotifyBaseUrl());
            existing.setSecret(command.getSecret());
            existing.setDefaultTenantCode(defaultTenantCode);
            applicationMapper.update(existing);
        } else {
            AccountApplication app = new AccountApplication(
                    command.getAppCode(),
                    command.getName(),
                    command.getEntryUrl(),
                    command.getSsoCallbackUrl(),
                    command.getPermissionIframeUrl(),
                    command.getNotifyBaseUrl(),
                    command.getSecret(),
                    defaultTenantCode);
            applicationMapper.insert(app);
        }
        return requireApplication(command.getAppCode());
    }

    @Override
    public AccountUser requireUser(String userId) {
        AccountUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("user not found");
        }
        return user;
    }

    @Override
    public AccountApplication requireApplication(String appCode) {
        AccountApplication app = applicationMapper.selectByAppCode(appCode);
        if (app == null) {
            throw new IllegalArgumentException("application not found");
        }
        return app;
    }

    @Override
    public void authorize(String userId, String appCode) {
        if (!isAuthorized(userId, appCode)) {
            userApplicationMapper.insert(userId, appCode);
        }
    }

    @Override
    public void deauthorize(String userId, String appCode) {
        userApplicationMapper.delete(userId, appCode);
    }

    @Override
    public List<AccountApplication> findAuthorizedApplications(String userId) {
        return userApplicationMapper.selectAuthorizedApplications(userId);
    }

    @Override
    public boolean isAuthorized(String userId, String appCode) {
        return userApplicationMapper.countByUserAndApp(userId, appCode) > 0;
    }

    @Override
    public List<AccountUser> findUsers(String keyword, String status) {
        return userMapper.selectByKeyword(keyword, status);
    }

    @Override
    public PageResult<AccountUser> findUsersPage(UserPageQuery query) {
        long total = userMapper.count(query);
        List<AccountUser> items = total == 0
                ? java.util.Collections.emptyList()
                : userMapper.selectPage(query);
        return new PageResult<>(items, query.getPage(), query.getSize(), total);
    }

    @Override
    public List<AccountApplication> findApplications(String keyword, String status) {
        return applicationMapper.selectByKeyword(keyword, status);
    }

    @Override
    public void updateUserStatus(String userId, String status) {
        userMapper.updateStatus(userId, status);
    }

    @Override
    public void updateApplicationStatus(String appCode, String status) {
        applicationMapper.updateStatus(appCode, status);
    }

    @Override
    public void rotateApplicationSecret(String appCode, String newSecret, int newVersion) {
        applicationMapper.updateSecret(appCode, newSecret, newVersion);
    }

    @Override
    public AccountUser findUserByAccount(String account) {
        return userMapper.selectByAccount(account);
    }

    @Override
    public void setUserPassword(String userId, String encodedPassword) {
        userMapper.updatePassword(userId, encodedPassword);
    }
}
