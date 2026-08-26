package com.hypers.account.app;

import com.hypers.account.mapper.AccountApplicationMapper;
import com.hypers.account.mapper.AccountUserApplicationMapper;
import com.hypers.account.mapper.AccountUserMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;

@RequiredArgsConstructor
public class MyBatisAccountStore implements AccountStore {

    private final AccountUserMapper userMapper;
    private final AccountApplicationMapper applicationMapper;
    private final AccountUserApplicationMapper userApplicationMapper;

    @Override
    public AccountUser saveNewUser(SaveUserCommand command) {
        return saveNewUser(command, null);
    }

    @Override
    public AccountUser saveNewUser(SaveUserCommand command, String operatorId) {
        AccountUser user = new AccountUser(
                UUID.randomUUID().toString().replace("-", ""),
                command.getAccount(),
                command.getEmail(),
                command.getName(),
                command.getPhone());
        user.setCreatedBy(operatorId);
        try {
            userMapper.insert(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AccountAlreadyExistsException();
        }
        return requireUser(user.getId());
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
    public AccountUser updateUser(String userId,
                                  SaveUserCommand command,
                                  long expectedVersion,
                                  String operatorId) {
        AccountUser user = requireUser(userId);
        user.setAccount(command.getAccount());
        user.setEmail(command.getEmail());
        user.setName(command.getName());
        user.setPhone(command.getPhone());
        user.setUpdatedBy(operatorId);
        try {
            if (userMapper.updateVersioned(user, expectedVersion) == 0) {
                throw new ResourceVersionConflictException();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new AccountAlreadyExistsException();
        }
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
    public AccountUser updateUserStatus(String userId,
                                        String status,
                                        long expectedVersion,
                                        String operatorId) {
        requireUser(userId);
        if (userMapper.updateStatusVersioned(userId, status, expectedVersion, operatorId) == 0) {
            throw new ResourceVersionConflictException();
        }
        return requireUser(userId);
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
