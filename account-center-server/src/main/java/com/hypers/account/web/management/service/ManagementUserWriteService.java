package com.hypers.account.web.management.service;

import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.command.SaveUserCommand;
import com.hypers.account.app.model.AccountUser;
import com.hypers.account.app.service.UserManagementService;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.auth.AdminAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理端用户写操作编排：写库、审计与管理端保护规则。
 *
 * <p>账号重复、版本冲突、用户不存在由 {@link UserManagementService} 抛出带国际化消息键的业务异常；
 * 本服务只处理“不能禁用当前管理员”“至少保留一个管理员”等管理端规则。
 */
@Service
@RequiredArgsConstructor
public class ManagementUserWriteService {

    private final UserManagementService userService;
    private final AdminAuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    @Transactional
    public AccountUser create(SaveUserCommand command, String operatorId) {
        AccountUser user = userService.createManagedUser(command, operatorId);
        auditLogService.log(operatorId, "USER_CREATED", "USER", user.getId(),
                "{\"fields\":[\"account\",\"email\",\"name\",\"phone\"]}");
        return user;
    }

    @Transactional
    public AccountUser update(String userId,
                              SaveUserCommand command,
                              long expectedVersion,
                              String operatorId) {
        AccountUser user = userService.updateManagedUser(userId, command, expectedVersion, operatorId);
        auditLogService.log(operatorId, "USER_UPDATED", "USER", user.getId(),
                "{\"fields\":[\"account\",\"email\",\"name\",\"phone\"]}");
        return user;
    }

    @Transactional
    public AccountUser changeStatus(String userId,
                                    String status,
                                    long expectedVersion,
                                    String reason,
                                    String operatorId) {
        AccountUser current = userService.getUser(userId);
        if ("disabled".equals(status)) {
            if (userId.equals(operatorId)) {
                throw new BusinessException(ErrorCode.CURRENT_ADMIN_PROTECTED);
            }
            if (authorizationService.isAdmin(userId)
                    && authorizationService.lockAndCountEnabledAdmins() <= 1) {
                throw new BusinessException(ErrorCode.LAST_ADMIN_PROTECTED);
            }
        }
        if (status.equals(current.getStatus()) && expectedVersion == current.getVersion()) {
            return current;
        }
        AccountUser user = userService.changeManagedUserStatus(
                userId, status, expectedVersion, operatorId);
        auditLogService.log(operatorId, "USER_STATUS_CHANGED", "USER", user.getId(),
                "{\"status\":\"" + status + "\",\"reasonProvided\":"
                        + (reason != null && !reason.trim().isEmpty()) + "}");
        return user;
    }
}