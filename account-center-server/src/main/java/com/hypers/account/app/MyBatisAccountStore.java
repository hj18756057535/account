package com.hypers.account.app;

import com.hypers.account.mapper.AccountApplicationMapper;
import com.hypers.account.mapper.AccountUserApplicationMapper;
import com.hypers.account.mapper.AccountUserMapper;
import com.hypers.account.mapper.SyncCommandMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;

@RequiredArgsConstructor
public class MyBatisAccountStore implements AccountStore {

    @Override
    public List<String> findExistingAccounts(List<String> accounts) {
        return accounts.isEmpty() ? java.util.Collections.emptyList() : userMapper.selectExistingAccounts(accounts);
    }

    @Override
    public List<AccountUser> saveNewUsers(List<SaveUserCommand> commands, String operatorId) {
        if (commands.isEmpty()) return java.util.Collections.emptyList();
        List<AccountUser> users = commands.stream().map(command -> {
            AccountUser user = new AccountUser(UUID.randomUUID().toString().replace("-", ""),
                    command.getAccount(), command.getEmail(), command.getName(), command.getPhone());
            user.setCreatedBy(operatorId);
            return user;
        }).toList();
        try {
            userMapper.insertBatch(users);
        } catch (DataIntegrityViolationException exception) {
            throw new AccountAlreadyExistsException();
        }
        return users;
    }

    private final AccountUserMapper userMapper;
    private final AccountApplicationMapper applicationMapper;
    private final AccountUserApplicationMapper userApplicationMapper;
    private final SyncCommandMapper syncCommandMapper;

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
    public AccountApplication createManagedApplication(SaveApplicationCommand command, String operatorId) {
        AccountApplication app = new AccountApplication(
                command.getAppCode(),
                command.getName(),
                command.getEntryUrl(),
                command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(),
                command.getNotifyBaseUrl(),
                command.getSecret(),
                normalizeTenant(command.getDefaultTenantCode()));
        app.setProtocolCapabilities(command.getProtocolCapabilities());
        app.setCreatedBy(operatorId);
        try {
            applicationMapper.insert(app);
        } catch (DataIntegrityViolationException exception) {
            throw new ApplicationAlreadyExistsException();
        }
        return requireApplication(command.getAppCode());
    }

    @Override
    public AccountApplication updateManagedApplication(SaveApplicationCommand command, String operatorId) {
        requireApplication(command.getAppCode());
        SaveApplicationCommand normalized = new SaveApplicationCommand(
                command.getAppCode(), command.getName(), command.getEntryUrl(), command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(), command.getNotifyBaseUrl(),
                normalizeTenant(command.getDefaultTenantCode()), command.getProtocolCapabilities(),
                null, command.getExpectedVersion());
        if (applicationMapper.updateManaged(normalized, operatorId) == 0) {
            throw new ResourceVersionConflictException();
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
    public List<ApplicationAccess> findApplicationAccess(String userId) {
        return userApplicationMapper.selectAccessByUser(userId);
    }

    @Override
    public ApplicationAccess requireApplicationAccess(String userId, String appCode) {
        ApplicationAccess access = userApplicationMapper.selectAccess(userId, appCode);
        if (access == null) {
            throw new IllegalArgumentException("application access not found");
        }
        return access;
    }

    @Override
    public ApplicationAccess saveApplicationAccess(String userId,
                                                   String appCode,
                                                   String desiredStatus,
                                                   long expectedVersion,
                                                   String operatorId) {
        ApplicationAccess existing = userApplicationMapper.selectAccess(userId, appCode);
        if (existing == null) {
            if (expectedVersion != 0) {
                throw new ResourceVersionConflictException();
            }
            try {
                userApplicationMapper.insertAccess(userId, appCode, desiredStatus, operatorId);
            } catch (DataIntegrityViolationException exception) {
                throw new ResourceVersionConflictException();
            }
        } else if (userApplicationMapper.updateAccessVersioned(
                userId, appCode, desiredStatus, expectedVersion, operatorId) == 0) {
            throw new ResourceVersionConflictException();
        }
        return requireApplicationAccess(userId, appCode);
    }

    @Override
    public void saveSyncCommand(ApplicationSyncCommand command) {
        syncCommandMapper.insert(command);
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
    public AccountApplication updateManagedApplicationStatus(String appCode,
                                                             String status,
                                                             long expectedVersion,
                                                             String operatorId) {
        requireApplication(appCode);
        if (applicationMapper.updateStatusVersioned(appCode, status, expectedVersion, operatorId) == 0) {
            throw new ResourceVersionConflictException();
        }
        return requireApplication(appCode);
    }

    @Override
    public void rotateApplicationSecret(String appCode, String newSecret, int newVersion) {
        applicationMapper.updateSecret(appCode, newSecret, newVersion);
    }

    @Override
    public AccountApplication rotateManagedApplicationSecret(String appCode,
                                                              String newSecret,
                                                              int newSecretVersion,
                                                              long expectedVersion,
                                                              String operatorId) {
        requireApplication(appCode);
        if (applicationMapper.rotateSecretVersioned(
                appCode, newSecret, newSecretVersion, expectedVersion, operatorId) == 0) {
            throw new ResourceVersionConflictException();
        }
        return requireApplication(appCode);
    }

    @Override
    public AccountApplication revokeManagedApplicationSecret(String appCode,
                                                              long expectedVersion,
                                                              String operatorId) {
        requireApplication(appCode);
        if (applicationMapper.revokeSecretVersioned(appCode, expectedVersion, operatorId) == 0) {
            throw new ResourceVersionConflictException();
        }
        return requireApplication(appCode);
    }

    @Override
    public AccountUser findUserByAccount(String account) {
        return userMapper.selectByAccount(account);
    }

    @Override
    public void setUserPassword(String userId, String encodedPassword) {
        userMapper.updatePassword(userId, encodedPassword);
    }

    private String normalizeTenant(String tenantCode) {
        return Optional.ofNullable(tenantCode)
                .filter(value -> !value.trim().isEmpty())
                .map(String::trim)
                .orElse("default");
    }
}
