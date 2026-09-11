package com.hypers.account.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.contract.MenuPermissionProtocol;
import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import com.hypers.account.web.management.ApiException;
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

    private final AccountDirectoryService directory;
    private final ApplicationSyncStore syncStore;
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
                          String locale,
                          String idempotencyKey,
                          String operatorId) {
        requireOperator(operatorId);
        if (syncStore.lockApplication(appCode) == null || syncStore.lockAccess(userId, appCode) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.resourceNotFound");
        }
        Subject subject = requireSubject(userId, appCode);
        if (subject.access.getVersion() != expectedAccessVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_VERSION_CONFLICT", "error.accessConflict");
        }
        validateSelectedCodes(selectedCodes);
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
                .build();
        try {
            MenuPermissionSnapshot snapshot = client.replace(subject.application, idempotencyKey, command);
            audit.log(operatorId, "APPLICATION_MENU_PERMISSION_REPLACED", "USER_APPLICATION",
                    userId + ":" + appCode,
                    auditDetail(expectedAccessVersion, expectedCatalogRevision,
                            expectedPermissionRevision, selectedCodes, snapshot));
            return new Result(subject.access.getVersion(), snapshot);
        } catch (ApplicationMenuPermissionFailure failure) {
            throw apiFailure(failure);
        }
    }

    private Subject requireSubject(String userId, String appCode) {
        try {
            AccountUser user = directory.getUser(userId);
            AccountApplication application = directory.getApplication(appCode);
            ApplicationAccess access = directory.getUserApplicationAccess(userId, appCode);
            ApplicationUserState state = syncStore.state(userId, appCode);
            boolean capability = application.getProtocolCapabilities() != null
                    && java.util.Arrays.stream(application.getProtocolCapabilities().split(","))
                    .map(String::trim).anyMatch(MenuPermissionProtocol.VERSION::equals);
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
        } catch (ApiException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.resourceNotFound");
        }
    }

    private void requireOperator(String operatorId) {
        try {
            if (!"enabled".equals(directory.getUser(operatorId).getStatus())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "error.accessDenied");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "error.accessDenied");
        }
    }

    private void validateSelectedCodes(List<String> selectedCodes) {
        if (selectedCodes == null || selectedCodes.size() > MenuPermissionProtocol.MAX_SELECTED_CODES
                || new LinkedHashSet<>(selectedCodes).size() != selectedCodes.size()
                || selectedCodes.stream().anyMatch(code -> !printableAscii(code, 1, 128))) {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("selectedCodes", "validation.menuPermissionCodes");
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_FAILED", "error.validation", fields);
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
                               MenuPermissionSnapshot result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("accessVersion", accessVersion);
        detail.put("catalogRevision", catalogRevision);
        detail.put("permissionRevision", permissionRevision);
        detail.put("selectedCount", selectedCodes.size());
        detail.put("resultPermissionRevision", result.getPermissionRevision());
        detail.put("selectionDigest", digest(selectedCodes));
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

    private ApiException prerequisite() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "MENU_PERMISSION_PREREQUISITE_NOT_MET", "menuPermission.prerequisite");
    }

    private ApiException apiFailure(ApplicationMenuPermissionFailure failure) {
        return new ApiException(HttpStatus.valueOf(failure.getStatus()), failure.getCode(), switch (failure.getCode()) {
            case "SUBJECT_NOT_FOUND" -> "menuPermission.subjectNotFound";
            case "SUBJECT_UNMANAGEABLE" -> "menuPermission.subjectUnmanageable";
            case "PERMISSION_REVISION_CONFLICT" -> "menuPermission.revisionConflict";
            case "IDEMPOTENCY_CONFLICT" -> "menuPermission.idempotencyConflict";
            case "PERMISSION_CODE_INVALID" -> "menuPermission.codeInvalid";
            case "CATALOG_INVALID" -> "menuPermission.catalogInvalid";
            case "PAYLOAD_TOO_LARGE" -> "menuPermission.tooLarge";
            case "PROTOCOL_VERSION_UNSUPPORTED" -> "menuPermission.protocolUnsupported";
            default -> "menuPermission.unavailable";
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
