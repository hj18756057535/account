package com.hypers.account.web.management;

import com.hypers.account.app.AccountAlreadyExistsException;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.ResourceVersionConflictException;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.auth.AdminAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagementUserWriteService {

    private final AccountDirectoryService directoryService;
    private final AdminAuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    @Transactional
    public AccountUser create(SaveUserCommand command, String operatorId) {
        try {
            AccountUser user = directoryService.createManagedUser(command, operatorId);
            auditLogService.log(operatorId, "USER_CREATED", "USER", user.getId(),
                    "{\"fields\":[\"account\",\"email\",\"name\",\"phone\"]}");
            return user;
        } catch (AccountAlreadyExistsException exception) {
            throw accountConflict();
        }
    }

    @Transactional
    public AccountUser update(String userId,
                              SaveUserCommand command,
                              long expectedVersion,
                              String operatorId) {
        try {
            AccountUser user = directoryService.updateManagedUser(userId, command, expectedVersion, operatorId);
            auditLogService.log(operatorId, "USER_UPDATED", "USER", user.getId(),
                    "{\"fields\":[\"account\",\"email\",\"name\",\"phone\"]}");
            return user;
        } catch (AccountAlreadyExistsException exception) {
            throw accountConflict();
        } catch (ResourceVersionConflictException exception) {
            throw versionConflict();
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
    }

    @Transactional
    public AccountUser changeStatus(String userId,
                                    String status,
                                    long expectedVersion,
                                    String reason,
                                    String operatorId) {
        AccountUser current;
        try {
            current = directoryService.getUser(userId);
        } catch (IllegalArgumentException exception) {
            throw notFound();
        }
        if ("disabled".equals(status)) {
            if (userId.equals(operatorId)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "CURRENT_ADMIN_PROTECTED", "不能禁用当前登录的管理员账号");
            }
            if (authorizationService.isAdmin(userId)
                    && authorizationService.lockAndCountEnabledAdmins() <= 1) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "LAST_ADMIN_PROTECTED", "至少需要保留一个已启用的管理员账号");
            }
        }
        if (status.equals(current.getStatus()) && expectedVersion == current.getVersion()) {
            return current;
        }
        try {
            AccountUser user = directoryService.changeManagedUserStatus(
                    userId, status, expectedVersion, operatorId);
            auditLogService.log(operatorId, "USER_STATUS_CHANGED", "USER", user.getId(),
                    "{\"status\":\"" + status + "\",\"reasonProvided\":"
                            + (reason != null && !reason.trim().isEmpty()) + "}");
            return user;
        } catch (ResourceVersionConflictException exception) {
            throw versionConflict();
        }
    }

    private ApiException accountConflict() {
        return new ApiException(HttpStatus.CONFLICT,
                "ACCOUNT_ALREADY_EXISTS", "该登录账号已存在");
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT,
                "RESOURCE_VERSION_CONFLICT", "用户已被其他操作更新，请刷新后重试");
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "未找到指定用户");
    }
}
