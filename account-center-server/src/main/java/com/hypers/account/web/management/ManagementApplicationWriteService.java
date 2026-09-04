package com.hypers.account.web.management;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.ApplicationAccess;
import com.hypers.account.app.ApplicationAlreadyExistsException;
import com.hypers.account.app.ApplicationSyncCommand;
import com.hypers.account.app.ResourceVersionConflictException;
import com.hypers.account.app.SaveApplicationCommand;
import com.hypers.account.audit.AuditLogService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagementApplicationWriteService {

    private final AccountDirectoryService directoryService;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public CreatedApplication create(SaveApplicationCommand command, String operatorId) {
        String secret = generateSecret();
        SaveApplicationCommand withSecret = withSecret(command, secret);
        try {
            AccountApplication application = directoryService.createManagedApplication(withSecret, operatorId);
            auditLogService.log(operatorId, "APPLICATION_CREATED", "APPLICATION", application.getAppCode(),
                    "{\"fields\":[\"name\",\"urls\",\"protocolCapabilities\"]}");
            return new CreatedApplication(application, secret);
        } catch (ApplicationAlreadyExistsException exception) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "APPLICATION_ALREADY_EXISTS", "error.applicationExists");
        } catch (IllegalArgumentException exception) {
            throw validation("url", "validation.url");
        }
    }

    @Transactional
    public AccountApplication update(SaveApplicationCommand command, String operatorId) {
        try {
            AccountApplication application = directoryService.updateManagedApplication(command, operatorId);
            auditLogService.log(operatorId, "APPLICATION_UPDATED", "APPLICATION", application.getAppCode(),
                    "{\"fields\":[\"name\",\"urls\",\"protocolCapabilities\"]}");
            return application;
        } catch (ResourceVersionConflictException exception) {
            throw versionConflict();
        } catch (IllegalArgumentException exception) {
            if ("application not found".equals(exception.getMessage())) {
                throw notFound();
            }
            throw validation("url", "validation.url");
        }
    }

    @Transactional
    public AccountApplication changeStatus(String appCode,
                                           String status,
                                           long expectedVersion,
                                           String reason,
                                           String operatorId) {
        try {
            AccountApplication current = directoryService.getApplication(appCode);
            if (status.equals(current.getStatus()) && expectedVersion == current.getVersion()) {
                return current;
            }
            AccountApplication application = directoryService.changeManagedApplicationStatus(
                    appCode, status, expectedVersion, operatorId);
            auditLogService.log(operatorId, "APPLICATION_STATUS_CHANGED", "APPLICATION", appCode,
                    "{\"status\":\"" + status + "\",\"reasonProvided\":"
                            + (reason != null && !reason.trim().isEmpty()) + "}");
            return application;
        } catch (ResourceVersionConflictException exception) {
            throw versionConflict();
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    @Transactional
    public CreatedApplication rotateSecret(String appCode,
                                           long expectedVersion,
                                           String operatorId) {
        String secret = generateSecret();
        try {
            AccountApplication application = directoryService.rotateManagedApplicationSecret(
                    appCode, secret, expectedVersion, operatorId);
            auditLogService.log(operatorId, "APPLICATION_SECRET_ROTATED", "APPLICATION", appCode,
                    "{\"secretVersion\":" + application.getSecretVersion() + "}");
            return new CreatedApplication(application, secret);
        } catch (ResourceVersionConflictException exception) {
            throw versionConflict();
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    @Transactional
    public AccountApplication revokeSecret(String appCode,
                                           long expectedVersion,
                                           String operatorId) {
        try {
            AccountApplication application = directoryService.revokeManagedApplicationSecret(
                    appCode, expectedVersion, operatorId);
            auditLogService.log(operatorId, "APPLICATION_SECRET_REVOKED", "APPLICATION", appCode,
                    "{\"secretState\":\"revoked\"}");
            return application;
        } catch (ResourceVersionConflictException exception) {
            throw versionConflict();
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    @Transactional
    public ApplicationAccess changeAccess(String userId,
                                          String appCode,
                                          String desiredStatus,
                                          long expectedVersion,
                                          String traceId,
                                          String operatorId) {
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
        try {
            ApplicationAccess access = directoryService.changeUserApplicationAccess(
                    userId, appCode, desiredStatus, expectedVersion, operatorId, syncCommand);
            auditLogService.log(operatorId, "APPLICATION_ACCESS_CHANGED", "USER_APPLICATION",
                    userId + ":" + appCode,
                    "{\"desiredStatus\":\"" + desiredStatus
                            + "\",\"syncState\":\"pending_application_adaptation\"}");
            return access;
        } catch (ResourceVersionConflictException exception) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "RESOURCE_VERSION_CONFLICT", "error.accessConflict");
        } catch (IllegalStateException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "APPLICATION_DISABLED", "error.applicationDisabled");
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "RESOURCE_NOT_FOUND", "error.resourceNotFound");
        }
    }

    private SaveApplicationCommand withSecret(SaveApplicationCommand command, String secret) {
        return new SaveApplicationCommand(
                command.getAppCode(), command.getName(), command.getEntryUrl(), command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(), command.getNotifyBaseUrl(), command.getDefaultTenantCode(),
                command.getProtocolCapabilities(), secret, command.getExpectedVersion());
    }

    private String generateSecret() {
        byte[] value = new byte[32];
        secureRandom.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT,
                "RESOURCE_VERSION_CONFLICT", "error.applicationConflict");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND,
                "APPLICATION_NOT_FOUND", "error.applicationNotFound");
    }

    private ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED", "error.validation",
                java.util.Collections.singletonMap(field, message));
    }

    @lombok.Getter
    @RequiredArgsConstructor
    public static class CreatedApplication {
        private final AccountApplication application;
        private final String secret;
    }
}
