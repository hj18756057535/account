package com.hypers.account.web.management.service;

import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.command.SaveApplicationCommand;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.model.ApplicationAccess;
import com.hypers.account.app.model.ApplicationSyncCommand;
import com.hypers.account.app.service.ApplicationManagementService;
import com.hypers.account.app.service.ApplicationSynchronizationService;
import com.hypers.account.app.service.UserApplicationAccessService;
import com.hypers.account.audit.AuditLogService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端应用写操作编排：生成密钥、写库、审计与准入同步。
 *
 * <p>业务异常（应用不存在、编码重复、版本冲突）由应用服务抛出，已携带错误码和国际化消息键，
 * 这里只补充管理端特有的地址校验错误。
 */
@Service
@RequiredArgsConstructor
public class ManagementApplicationWriteService {

    private final ApplicationManagementService applicationService;
    private final UserApplicationAccessService accessService;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final ApplicationSynchronizationService synchronizationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public CreatedApplication create(SaveApplicationCommand command, String operatorId) {
        String secret = generateSecret();
        try {
            AccountApplication application = applicationService.createManagedApplication(withSecret(command, secret), operatorId);
            auditLogService.log(operatorId, "APPLICATION_CREATED", "APPLICATION", application.getAppCode(),
                    "{\"fields\":[\"name\",\"urls\"]}");
            return new CreatedApplication(application, secret);
        } catch (IllegalArgumentException exception) {
            throw validation("url", "validation.url");
        }
    }

    @Transactional
    public AccountApplication update(SaveApplicationCommand command, String operatorId) {
        try {
            AccountApplication application = applicationService.updateManagedApplication(command, operatorId);
            auditLogService.log(operatorId, "APPLICATION_UPDATED", "APPLICATION", application.getAppCode(),
                    "{\"fields\":[\"name\",\"urls\"]}");
            return application;
        } catch (IllegalArgumentException exception) {
            throw validation("url", "validation.url");
        }
    }

    @Transactional
    public AccountApplication changeStatus(String appCode,
                                           String status,
                                           long expectedVersion,
                                           String reason,
                                           String operatorId) {
        AccountApplication current = applicationService.getApplication(appCode);
        if (status.equals(current.getStatus()) && expectedVersion == current.getVersion()) {
            return current;
        }
        AccountApplication application = applicationService.changeManagedApplicationStatus(
                appCode, status, expectedVersion, operatorId);
        auditLogService.log(operatorId, "APPLICATION_STATUS_CHANGED", "APPLICATION", appCode,
                "{\"status\":\"" + status + "\",\"reasonProvided\":"
                        + (reason != null && !reason.trim().isEmpty()) + "}");
        return application;
    }

    @Transactional
    public CreatedApplication rotateSecret(String appCode,
                                           long expectedVersion,
                                           String operatorId) {
        String secret = generateSecret();
        AccountApplication application = applicationService.rotateManagedApplicationSecret(
                appCode, secret, expectedVersion, operatorId);
        auditLogService.log(operatorId, "APPLICATION_SECRET_ROTATED", "APPLICATION", appCode,
                "{\"secretVersion\":" + application.getSecretVersion() + "}");
        return new CreatedApplication(application, secret);
    }

    @Transactional
    public AccountApplication revokeSecret(String appCode,
                                           long expectedVersion,
                                           String operatorId) {
        AccountApplication application = applicationService.revokeManagedApplicationSecret(
                appCode, expectedVersion, operatorId);
        auditLogService.log(operatorId, "APPLICATION_SECRET_REVOKED", "APPLICATION", appCode,
                "{\"secretState\":\"revoked\"}");
        return application;
    }

    @Transactional
    public ApplicationAccess changeAccess(String userId,
                                          String appCode,
                                          String desiredStatus,
                                          long expectedVersion,
                                          String traceId,
                                          String operatorId) {
        return changeAccess(userId, appCode, desiredStatus, expectedVersion, traceId, operatorId, false);
    }

    @Transactional
    public ApplicationAccess changeAccess(String userId, String appCode, String desiredStatus, long expectedVersion,
                                          String traceId, String operatorId, boolean confirmPermissionReuse) {
        if (expectedVersion < 0 || expectedVersion >= 9007199254740991L)
            throw new BusinessException(ErrorCode.ACCESS_CONFLICT);
        synchronizationService.prepareChange(appCode);
        synchronizationService.requireReuseConfirmation(userId, appCode, desiredStatus, confirmPermissionReuse);
        String commandId = UUID.randomUUID().toString().replace("-", "");
        Instant now = clock.instant();
        ApplicationSyncCommand syncCommand = new ApplicationSyncCommand(
                commandId,
                commandId,
                userId,
                appCode,
                desiredStatus,
                expectedVersion + 1,
                "pending_application_adaptation",
                0,
                traceId,
                now);
        ApplicationAccess access = accessService.changeUserApplicationAccess(
                userId, appCode, desiredStatus, expectedVersion, operatorId, syncCommand);
        if (access.getSyncCommandId() != null) {
            access.setIntegrationStatus(synchronizationService.created(commandId, operatorId));
        }
        synchronizationService.populateConfirmation(access);
        synchronizationService.decorate(access, applicationService.getApplication(appCode));
        auditLogService.log(operatorId, "APPLICATION_ACCESS_CHANGED", "USER_APPLICATION",
                userId + ":" + appCode,
                "{\"desiredStatus\":\"" + desiredStatus
                        + "\",\"syncState\":\"" + access.getIntegrationStatus() + "\"}");
        return access;
    }

    private SaveApplicationCommand withSecret(SaveApplicationCommand command, String secret) {
        return new SaveApplicationCommand(
                command.getAppCode(), command.getName(), command.getEntryUrl(), command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(), command.getNotifyBaseUrl(), command.getDefaultTenantCode(), secret, command.getExpectedVersion());
    }

    private String generateSecret() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private BusinessException validation(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                java.util.Collections.singletonMap(field, message));
    }

    /** 新建或轮换密钥后的返回值：应用与一次性明文密钥，密钥不进入 toString。 */
    @Getter
    @RequiredArgsConstructor
    public static class CreatedApplication {
        private final AccountApplication application;
        private final String secret;
    }
}