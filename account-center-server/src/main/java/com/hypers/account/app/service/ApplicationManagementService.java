package com.hypers.account.app.service;

import com.hypers.account.app.command.RegisterApplicationCommand;
import com.hypers.account.app.command.SaveApplicationCommand;
import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.mapper.AccountApplicationMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 应用管理应用服务。
 *
 * <p>统一在写入前校验回调、入口等地址；读写直接使用 {@link AccountApplicationMapper}。
 * 区分两条写入路径：SSO 登记是按 appCode 的幂等 upsert，管理端写入使用显式版本控制。
 */
@Service
@RequiredArgsConstructor
public class ApplicationManagementService {

    private final AccountApplicationMapper applicationMapper;
    private final ApplicationUrlValidator urlValidator;

    /** SSO 登记路径：按 appCode 幂等写入，不校验版本。 */
    public AccountApplication registerApplication(RegisterApplicationCommand command) {
        urlValidator.validate(command);
        String defaultTenantCode = normalizeTenant(command.getDefaultTenantCode());
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
            applicationMapper.insert(new AccountApplication(
                    command.getAppCode(),
                    command.getName(),
                    command.getEntryUrl(),
                    command.getSsoCallbackUrl(),
                    command.getPermissionIframeUrl(),
                    command.getNotifyBaseUrl(),
                    command.getSecret(),
                    defaultTenantCode));
        }
        return require(command.getAppCode());
    }

    /** 管理端新建应用，appCode 重复时返回 409。 */
    public AccountApplication createManagedApplication(SaveApplicationCommand command, String operatorId) {
        urlValidator.validate(command);
        AccountApplication application = new AccountApplication(
                command.getAppCode(),
                command.getName(),
                command.getEntryUrl(),
                command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(),
                command.getNotifyBaseUrl(),
                command.getSecret(),
                normalizeTenant(command.getDefaultTenantCode()));
        application.setCreatedBy(operatorId);
        try {
            applicationMapper.insert(application);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_EXISTS);
        }
        return require(command.getAppCode());
    }

    /** 管理端按显式版本更新应用资料。 */
    public AccountApplication updateManagedApplication(SaveApplicationCommand command, String operatorId) {
        urlValidator.validate(command);
        require(command.getAppCode());
        SaveApplicationCommand normalized = new SaveApplicationCommand(
                command.getAppCode(), command.getName(), command.getEntryUrl(), command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(), command.getNotifyBaseUrl(),
                normalizeTenant(command.getDefaultTenantCode()),
                null, command.getExpectedVersion());
        if (applicationMapper.updateManaged(normalized, operatorId) == 0) {
            throw new BusinessException(ErrorCode.APPLICATION_CONFLICT);
        }
        return require(command.getAppCode());
    }

    /** 管理端按显式版本调整应用启停状态。 */
    public AccountApplication changeManagedApplicationStatus(String appCode,
                                                             String status,
                                                             long expectedVersion,
                                                             String operatorId) {
        require(appCode);
        if (applicationMapper.updateStatusVersioned(appCode, status, expectedVersion, operatorId) == 0) {
            throw new BusinessException(ErrorCode.APPLICATION_CONFLICT);
        }
        return require(appCode);
    }

    public AccountApplication getApplication(String appCode) {
        return require(appCode);
    }

    public List<AccountApplication> findApplications(String keyword, String status) {
        return applicationMapper.selectByKeyword(keyword, status);
    }

    /** 按显式版本轮换客户端密钥，密钥版本自增；明文密钥由调用方生成并负责展示。 */
    public AccountApplication rotateManagedApplicationSecret(String appCode,
                                                             String secret,
                                                             long expectedVersion,
                                                             String operatorId) {
        AccountApplication application = require(appCode);
        int currentVersion = application.getSecretVersion() == null ? 0 : application.getSecretVersion();
        if (applicationMapper.rotateSecretVersioned(
                appCode, secret, currentVersion + 1, expectedVersion, operatorId) == 0) {
            throw new BusinessException(ErrorCode.APPLICATION_CONFLICT);
        }
        return require(appCode);
    }

    /** 吊销客户端密钥，不删除应用记录。 */
    public AccountApplication revokeManagedApplicationSecret(String appCode,
                                                             long expectedVersion,
                                                             String operatorId) {
        require(appCode);
        if (applicationMapper.revokeSecretVersioned(appCode, expectedVersion, operatorId) == 0) {
            throw new BusinessException(ErrorCode.APPLICATION_CONFLICT);
        }
        return require(appCode);
    }

    private AccountApplication require(String appCode) {
        AccountApplication application = applicationMapper.selectByAppCode(appCode);
        if (application == null) {
            throw new BusinessException(ErrorCode.APPLICATION_NOT_FOUND);
        }
        return application;
    }

    private String normalizeTenant(String tenantCode) {
        return Optional.ofNullable(tenantCode)
                .filter(value -> !value.trim().isEmpty())
                .map(String::trim)
                .orElse("default");
    }
}
