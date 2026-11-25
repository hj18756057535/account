package com.hypers.account.app.exception;

import lombok.Getter;

/**
 * 业务错误码。
 *
 * <p>每个常量同时定义对外错误码、HTTP 状态和国际化消息键，是异常契约的唯一来源：
 * 服务层只抛出 {@link BusinessException}，展示文案由 web 层按请求语言解析。
 */
@Getter
public enum ErrorCode {

    // 登录与票据
    INVALID_CREDENTIALS(401, "INVALID_CREDENTIALS", "error.credentials"),
    ACCOUNT_DISABLED(403, "ACCOUNT_DISABLED", "error.accountDisabled"),
    PASSWORD_NOT_SET(403, "PASSWORD_NOT_SET", "error.passwordNotSet"),
    TICKET_INVALID(400, "TICKET_INVALID", "error.ticket"),

    // 用户与应用
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "error.userNotFound"),
    APPLICATION_NOT_FOUND(404, "APPLICATION_NOT_FOUND", "error.applicationNotFound"),
    RESOURCE_NOT_FOUND(404, "RESOURCE_NOT_FOUND", "error.resourceNotFound"),
    ACCOUNT_ALREADY_EXISTS(409, "ACCOUNT_ALREADY_EXISTS", "error.accountExists"),
    APPLICATION_ALREADY_EXISTS(409, "APPLICATION_ALREADY_EXISTS", "error.applicationExists"),
    USER_CONFLICT(409, "RESOURCE_VERSION_CONFLICT", "error.userConflict"),
    APPLICATION_CONFLICT(409, "RESOURCE_VERSION_CONFLICT", "error.applicationConflict"),
    ACCESS_CONFLICT(409, "RESOURCE_VERSION_CONFLICT", "error.accessConflict"),
    USER_DISABLED(403, "USER_DISABLED", "error.accessDenied"),
    ACCESS_DENIED(403, "ACCESS_DENIED", "error.accessDenied"),
    APPLICATION_DISABLED(422, "APPLICATION_DISABLED", "error.applicationDisabled"),
    VERSIONED_ACCESS_REQUIRED(400, "VERSIONED_ACCESS_REQUIRED", "error.invalidRequest"),
    VALIDATION_FAILED(422, "VALIDATION_FAILED", "error.validation"),
    UNREADABLE_BODY(422, "VALIDATION_FAILED", "error.unreadable"),

    // 管理后台
    MANAGEMENT_ACCESS_DENIED(403, "MANAGEMENT_ACCESS_DENIED", "error.managementDenied"),
    CURRENT_ADMIN_PROTECTED(422, "CURRENT_ADMIN_PROTECTED", "error.currentAdmin"),
    LAST_ADMIN_PROTECTED(422, "LAST_ADMIN_PROTECTED", "error.lastAdmin"),
    AUDIT_EVENT_NOT_FOUND(404, "AUDIT_EVENT_NOT_FOUND", "error.auditNotFound"),
    CSRF_INVALID(403, "CSRF_INVALID", "error.csrf"),
    IDEMPOTENCY_IN_PROGRESS(409, "IDEMPOTENCY_IN_PROGRESS", "error.idempotencyInProgress"),
    IDEMPOTENCY_KEY_REUSED(409, "IDEMPOTENCY_KEY_REUSED", "error.idempotencyReused"),
    IDEMPOTENCY_RESULT_NOT_REPLAYABLE(409, "IDEMPOTENCY_RESULT_NOT_REPLAYABLE", "error.sensitiveReplay"),

    // Excel 用户导入
    IMPORT_CONFLICT(409, "IMPORT_CONFLICT", "import.conflict"),
    IMPORT_DISABLED(404, "IMPORT_DISABLED", "import.disabled"),
    IMPORT_EXPIRED(410, "IMPORT_EXPIRED", "import.expired"),
    IMPORT_FILE_INVALID(422, "IMPORT_FILE_INVALID", "import.invalidFile"),
    IMPORT_LIMIT_EXCEEDED(413, "IMPORT_LIMIT_EXCEEDED", "import.tooLarge"),
    IMPORT_NOT_FOUND(404, "IMPORT_NOT_FOUND", "import.notFound"),
    USER_IMPORT_DISABLED(403, "USER_IMPORT_DISABLED", "import.disabled"),

    // 应用目录
    DIRECTORY_TOO_LARGE(503, "DIRECTORY_TOO_LARGE", "directory.tooLarge"),
    DIRECTORY_NOT_CONFIGURED(503, "DIRECTORY_NOT_CONFIGURED", "directory.notConfigured"),
    DIRECTORY_AUTH_FAILED(503, "DIRECTORY_AUTH_FAILED", "directory.authFailed"),
    DIRECTORY_PROVIDER_MISSING(503, "DIRECTORY_PROVIDER_MISSING", "directory.providerMissing"),
    DIRECTORY_UNAVAILABLE(503, "DIRECTORY_UNAVAILABLE", "directory.unavailable"),
    DIRECTORY_RESULT_INVALID(422, "DIRECTORY_RESULT_INVALID", "directory.invalid"),
    DIRECTORY_PREVIEW_EXPIRED(410, "DIRECTORY_PREVIEW_EXPIRED", "directory.expired"),
    DIRECTORY_PREVIEW_CHANGED(409, "DIRECTORY_PREVIEW_CHANGED", "directory.changed"),
    DIRECTORY_IDENTITY_CONFLICT(409, "DIRECTORY_IDENTITY_CONFLICT", "directory.identityConflict"),

    // 菜单权限：本地校验 + 下游协议失败
    MENU_PERMISSION_PREREQUISITE_NOT_MET(422, "MENU_PERMISSION_PREREQUISITE_NOT_MET", "menuPermission.prerequisite"),
    MENU_SUBJECT_NOT_FOUND(404, "SUBJECT_NOT_FOUND", "menuPermission.subjectNotFound"),
    MENU_SUBJECT_UNMANAGEABLE(422, "SUBJECT_UNMANAGEABLE", "menuPermission.subjectUnmanageable"),
    MENU_PERMISSION_REVISION_CONFLICT(409, "PERMISSION_REVISION_CONFLICT", "menuPermission.revisionConflict"),
    MENU_IDEMPOTENCY_CONFLICT(409, "IDEMPOTENCY_CONFLICT", "menuPermission.idempotencyConflict"),
    MENU_PERMISSION_CODE_INVALID(422, "PERMISSION_CODE_INVALID", "menuPermission.codeInvalid"),
    MENU_CATALOG_INVALID(422, "CATALOG_INVALID", "menuPermission.catalogInvalid"),
    MENU_PAYLOAD_TOO_LARGE(413, "PAYLOAD_TOO_LARGE", "menuPermission.tooLarge"),
    MENU_PROTOCOL_VERSION_UNSUPPORTED(426, "PROTOCOL_VERSION_UNSUPPORTED", "menuPermission.protocolUnsupported"),
    MENU_PROVIDER_UNAVAILABLE(503, "PERMISSION_PROVIDER_UNAVAILABLE", "menuPermission.unavailable"),

    // 用户状态同步
    SYNC_CONFLICT(409, "RESOURCE_VERSION_CONFLICT", "sync.conflict"),
    SYNC_REUSE_CONFIRMATION_REQUIRED(409, "SYNC_REUSE_CONFIRMATION_REQUIRED", "sync.reuse"),
    SYNC_DISABLED(422, "SYNC_DISABLED", "sync.unavailable"),
    SYNC_USER_DISABLED(422, "SYNC_USER_DISABLED", "sync.unavailable"),
    SYNC_IDENTITY_CONFLICT(422, "SYNC_IDENTITY_CONFLICT", "sync.unavailable"),
    SYNC_PAYLOAD_TOO_LARGE(422, "SYNC_PAYLOAD_TOO_LARGE", "sync.unavailable"),
    SYNC_TARGET_UNAVAILABLE(422, "SYNC_TARGET_UNAVAILABLE", "sync.unavailable"),
    SYNC_TARGET_CHANGED(422, "SYNC_TARGET_CHANGED", "sync.unavailable"),
    SYNC_DEPENDENCY_UNAVAILABLE(422, "SYNC_DEPENDENCY_UNAVAILABLE", "sync.unavailable"),
    SYNC_RESPONSE_TOO_LARGE(422, "SYNC_RESPONSE_TOO_LARGE", "sync.unavailable"),
    SYNC_RESPONSE_REJECTED(422, "SYNC_RESPONSE_REJECTED", "sync.unavailable"),
    SYNC_RESULT_INVALID(422, "SYNC_RESULT_INVALID", "sync.unavailable"),
    /** 未分类的同步失败，保留给无法映射到具体原因的内部错误。 */
    SYNC_UNAVAILABLE(422, "SYNC_UNAVAILABLE", "sync.unavailable");

    private final int status;
    private final String code;
    private final String messageKey;

    ErrorCode(int status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }
}
