package com.hypers.account.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.model.AccountUser;
import com.hypers.account.app.model.ApplicationAccess;
import com.hypers.account.app.model.ApplicationMenuPermissionFailure;
import com.hypers.account.app.model.ApplicationUserState;
import com.hypers.account.mapper.ApplicationSyncMapper;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.contract.MenuPermissionProtocol;
import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationMenuPermissionService {

    private final UserManagementService userService;
    private final ApplicationManagementService applicationService;
    private final UserApplicationAccessService accessService;
    private final ApplicationSyncMapper syncMapper;
    private final ApplicationMenuPermissionClient client;
    private final AuditLogService audit;
    private final ObjectMapper json;

    public Result query(String userId, String appCode, String locale, String operatorId) {
        requireOperator(operatorId);
        Subject subject = requireSubject(userId, appCode);
        try {
            MenuPermissionSnapshot snapshot = client.query(subject.application,
                    MenuPermissionQuery.builder()
                            .protocolVersion(MenuPermissionProtocol.VERSION)
                            .appCode(appCode)
                            .globalUserId(userId)
                            .localUserId(subject.state.getLocalUserId())
                            .account(subject.user.getAccount())
                            .locale(locale)
                            .build());
            return new Result(subject.access.getVersion(), snapshot);
        } catch (ApplicationMenuPermissionFailure failure) {
            throw apiFailure(failure);
        }
    }

    @Transactional
    public Result replace(String userId,
                          String appCode,
                          long expectedAccessVersion,
                          String expectedCatalogRevision,
                          String expectedPermissionRevision,
                          List<String> selectedCodes,
                          List<String> selectedRoleIds,
                          String locale,
                          String idempotencyKey,
                          String operatorId) {
        requireOperator(operatorId);
        if (syncMapper.lockApplication(appCode) == null || syncMapper.lockAccess(userId, appCode) == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Subject subject = requireSubject(userId, appCode);
        if (subject.access.getVersion() != expectedAccessVersion) {
            throw new BusinessException(ErrorCode.ACCESS_CONFLICT);
        }
        validateSelection(selectedCodes, "selectedCodes");
        if (selectedRoleIds != null) validateSelection(selectedRoleIds, "selectedRoleIds");
        MenuPermissionReplaceCommand command = MenuPermissionReplaceCommand.builder()
                .protocolVersion(MenuPermissionProtocol.VERSION)
                .appCode(appCode)
                .globalUserId(userId)
                .localUserId(subject.state.getLocalUserId())
                .account(subject.user.getAccount())
                .locale(locale)
                .expectedCatalogRevision(expectedCatalogRevision)
                .expectedPermissionRevision(expectedPermissionRevision)
                .selectedCodes(selectedCodes)
                .selectedRoleIds(selectedRoleIds)
                .build();
        try {
            MenuPermissionSnapshot snapshot = client.replace(subject.application, idempotencyKey, command);
            audit.log(operatorId, "APPLICATION_MENU_PERMISSION_REPLACED", "USER_APPLICATION",
                    userId + ":" + appCode,
                    auditDetail(expectedAccessVersion, expectedCatalogRevision,
                            expectedPermissionRevision, selectedCodes, selectedRoleIds, snapshot));
            return new Result(subject.access.getVersion(), snapshot);
        } catch (ApplicationMenuPermissionFailure failure) {
            throw apiFailure(failure);
        }
    }

    private Subject requireSubject(String userId, String appCode) {
        try {
            AccountUser user = userService.getUser(userId);
            AccountApplication application = applicationService.getApplication(appCode);
            ApplicationAccess access = accessService.getUserApplicationAccess(userId, appCode);
            ApplicationUserState state = syncMapper.state(userId, appCode);
            boolean capability = application.hasProviderConfiguration();
            if (!"enabled".equals(user.getStatus()) || !"enabled".equals(application.getStatus())
                    || !"active".equals(application.getSecretState()) || !capability
                    || !"enabled".equals(access.getDesiredStatus())
                    || !"succeeded".equals(access.getIntegrationStatus())
                    || !"enabled".equals(access.getAppliedStatus())
                    || access.getAppliedVersion() == null || access.getAppliedVersion() != access.getVersion()
                    || state == null || !"enabled".equals(state.getAppliedStatus())
                    || state.getAppliedVersion() != access.getVersion()
                    || state.getLocalUserId() == null || state.getLocalUserId().isBlank()) {
                throw prerequisite();
            }
            return new Subject(user, application, access, state);
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == ErrorCode.MENU_PERMISSION_PREREQUISITE_NOT_MET) throw exception;
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireOperator(String operatorId) {
        try {
            if (!"enabled".equals(userService.getUser(operatorId).getStatus())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        } catch (BusinessException exception) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void validateSelection(List<String> selectedCodes, String field) {
        if (selectedCodes == null || selectedCodes.size() > MenuPermissionProtocol.MAX_SELECTED_CODES
                || new LinkedHashSet<>(selectedCodes).size() != selectedCodes.size()
                || selectedCodes.stream().anyMatch(code -> !printableAscii(code, 1, 128))) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put(field, "validation.menuPermissionCodes");
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, fields);
        }
    }

    private boolean printableAscii(String value, int min, int max) {
        if (value == null || value.length() < min || value.length() > max) return false;
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length != value.length()) return false;
        for (byte item : bytes) if (item < 0x21 || item > 0x7e) return false;
        return true;
    }

    private String auditDetail(long accessVersion,
                               String catalogRevision,
                               String permissionRevision,
                               List<String> selectedCodes,
                               List<String> selectedRoleIds,
                               MenuPermissionSnapshot result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("accessVersion", accessVersion);
        detail.put("catalogRevision", catalogRevision);
        detail.put("permissionRevision", permissionRevision);
        detail.put("selectedCount", selectedCodes.size());
        detail.put("resultPermissionRevision", result.getPermissionRevision());
        detail.put("selectionDigest", digest(selectedCodes));
        if (selectedRoleIds != null) {
            detail.put("selectedRoleCount", selectedRoleIds.size());
            detail.put("roleSelectionDigest", digest(selectedRoleIds));
        }
        try {
            return json.writeValueAsString(detail);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize menu permission audit summary", exception);
        }
    }

    private String digest(List<String> selectedCodes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String code : selectedCodes) {
                digest.update(code.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            StringBuilder result = new StringBuilder();
            for (byte item : digest.digest()) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private BusinessException prerequisite() {
        return new BusinessException(ErrorCode.MENU_PERMISSION_PREREQUISITE_NOT_MET);
    }

    private BusinessException apiFailure(ApplicationMenuPermissionFailure failure) {
        return new BusinessException(switch (failure.getCode()) {
            case "SUBJECT_NOT_FOUND" -> ErrorCode.MENU_SUBJECT_NOT_FOUND;
            case "SUBJECT_UNMANAGEABLE" -> ErrorCode.MENU_SUBJECT_UNMANAGEABLE;
            case "PERMISSION_REVISION_CONFLICT" -> ErrorCode.MENU_PERMISSION_REVISION_CONFLICT;
            case "IDEMPOTENCY_CONFLICT" -> ErrorCode.MENU_IDEMPOTENCY_CONFLICT;
            case "PERMISSION_CODE_INVALID" -> ErrorCode.MENU_PERMISSION_CODE_INVALID;
            case "CATALOG_INVALID" -> ErrorCode.MENU_CATALOG_INVALID;
            case "PAYLOAD_TOO_LARGE" -> ErrorCode.MENU_PAYLOAD_TOO_LARGE;
            case "PROTOCOL_VERSION_UNSUPPORTED" -> ErrorCode.MENU_PROTOCOL_VERSION_UNSUPPORTED;
            default -> ErrorCode.MENU_PROVIDER_UNAVAILABLE;
        });
    }

    @Value
    public static class Result {
        long accessVersion;
        MenuPermissionSnapshot snapshot;
    }

    @Value
    private static class Subject {
        AccountUser user;
        AccountApplication application;
        ApplicationAccess access;
        ApplicationUserState state;
    }
}
