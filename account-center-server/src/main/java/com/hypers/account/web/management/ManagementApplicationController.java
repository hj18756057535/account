package com.hypers.account.web.management;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.ApplicationAccess;
import com.hypers.account.app.SaveApplicationCommand;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.web.AuthController;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "account.console-api.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ManagementApplicationController {

    private static final Set<String> APPLICATION_STATUSES =
            new java.util.HashSet<>(Arrays.asList("enabled", "disabled"));

    private final AccountDirectoryService directoryService;
    private final ManagementApplicationWriteService writeService;
    private final IdempotencyService idempotencyService;

    @GetMapping("/api/applications")
    public List<ApplicationResponse> listApplications(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status) {
        String normalizedStatus = normalize(status);
        if (normalizedStatus != null && !APPLICATION_STATUSES.contains(normalizedStatus)) {
            throw validation("status", "validation.applicationStatus");
        }
        String normalizedQuery = normalize(query);
        if (normalizedQuery != null && normalizedQuery.length() > 128) {
            throw validation("query", "validation.query.size");
        }
        return directoryService.findApplications(normalizedQuery, normalizedStatus).stream()
                .map(ApplicationResponse::from)
                .collect(Collectors.toList());
    }

    @PostMapping("/api/applications")
    public ResponseEntity<ApplicationSecretResponse> createApplication(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateApplicationRequest request,
            HttpSession session) {
        String operatorId = operatorId(session);
        ApplicationSecretResponse response = idempotencyService.executeNonReplayable(
                operatorId,
                "POST",
                "/api/applications",
                idempotencyKey,
                request,
                HttpStatus.CREATED.value(),
                ApplicationSecretResponse.class,
                () -> ApplicationSecretResponse.from(writeService.create(request.toCommand(), operatorId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/applications/{appCode}")
    public ApplicationResponse getApplication(@PathVariable String appCode) {
        try {
            return ApplicationResponse.from(directoryService.getApplication(appCode));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    "APPLICATION_NOT_FOUND", "error.applicationNotFound");
        }
    }

    @PutMapping("/api/applications/{appCode}")
    public ApplicationResponse updateApplication(
            @PathVariable String appCode,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateApplicationRequest request,
            HttpSession session) {
        validateAppCode(appCode);
        String operatorId = operatorId(session);
        return idempotencyService.execute(
                operatorId,
                "PUT",
                "/api/applications/" + appCode,
                idempotencyKey,
                request,
                HttpStatus.OK.value(),
                ApplicationResponse.class,
                () -> ApplicationResponse.from(writeService.update(
                        request.toCommand(appCode), operatorId)));
    }

    @PutMapping("/api/applications/{appCode}/status")
    public ApplicationResponse changeApplicationStatus(
            @PathVariable String appCode,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ChangeApplicationStatusRequest request,
            HttpSession session) {
        validateAppCode(appCode);
        String operatorId = operatorId(session);
        return idempotencyService.execute(
                operatorId,
                "PUT",
                "/api/applications/" + appCode + "/status",
                idempotencyKey,
                request,
                HttpStatus.OK.value(),
                ApplicationResponse.class,
                () -> ApplicationResponse.from(writeService.changeStatus(
                        appCode, request.getStatus(), request.getVersion(), request.getReason(), operatorId)));
    }

    @PostMapping("/api/applications/{appCode}/secret/rotate")
    public ApplicationSecretResponse rotateApplicationSecret(
            @PathVariable String appCode,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody VersionedReasonRequest request,
            HttpSession session) {
        validateAppCode(appCode);
        String operatorId = operatorId(session);
        return idempotencyService.executeNonReplayable(
                operatorId,
                "POST",
                "/api/applications/" + appCode + "/secret/rotate",
                idempotencyKey,
                request,
                HttpStatus.OK.value(),
                ApplicationSecretResponse.class,
                () -> ApplicationSecretResponse.from(writeService.rotateSecret(
                        appCode, request.getVersion(), operatorId)));
    }

    @PostMapping("/api/applications/{appCode}/secret/revoke")
    public ApplicationResponse revokeApplicationSecret(
            @PathVariable String appCode,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody VersionedReasonRequest request,
            HttpSession session) {
        validateAppCode(appCode);
        String operatorId = operatorId(session);
        return idempotencyService.execute(
                operatorId,
                "POST",
                "/api/applications/" + appCode + "/secret/revoke",
                idempotencyKey,
                request,
                HttpStatus.OK.value(),
                ApplicationResponse.class,
                () -> ApplicationResponse.from(writeService.revokeSecret(
                        appCode, request.getVersion(), operatorId)));
    }

    @GetMapping("/api/users/{userId}/application-access")
    public List<ApplicationAccessResponse> listApplicationAccess(@PathVariable String userId) {
        validateUserId(userId);
        Map<String, AccountApplication> applications = directoryService.findApplications(null, null).stream()
                .collect(Collectors.toMap(AccountApplication::getAppCode, Function.identity()));
        try {
            return directoryService.getUserApplicationAccess(userId).stream()
                    .map(access -> ApplicationAccessResponse.from(access, applications.get(access.getAppCode())))
                    .collect(Collectors.toList());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "error.userNotFound");
        }
    }

    @PutMapping("/api/users/{userId}/application-access/{appCode}")
    public ResponseEntity<ApplicationAccessResponse> changeApplicationAccess(
            @PathVariable String userId,
            @PathVariable String appCode,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ChangeApplicationAccessRequest request,
            HttpServletRequest servletRequest,
            HttpSession session) {
        validateUserId(userId);
        validateAppCode(appCode);
        String operatorId = operatorId(session);
        ApplicationAccessResponse response = idempotencyService.execute(
                operatorId,
                "PUT",
                "/api/users/" + userId + "/application-access/" + appCode,
                idempotencyKey,
                request,
                HttpStatus.ACCEPTED.value(),
                ApplicationAccessResponse.class,
                () -> ApplicationAccessResponse.from(
                        writeService.changeAccess(
                                userId,
                                appCode,
                                request.getStatus(),
                                request.getVersion(),
                                RequestTraceFilter.traceId(servletRequest),
                                operatorId),
                        directoryService.getApplication(appCode)));
        return ResponseEntity.accepted().body(response);
    }

    private String operatorId(HttpSession session) {
        AccountSessionUser user = (AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY);
        return user.getUserId();
    }

    private void validateAppCode(String appCode) {
        if (appCode == null || !appCode.matches("[a-z][a-z0-9-]{0,63}")) {
            throw validation("appCode", "validation.appCode.format");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isEmpty() || userId.length() > 64) {
            throw validation("userId", "validation.userId");
        }
    }

    private ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED", "error.validation",
                Collections.singletonMap(field, message));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Getter
    @Setter
    public static class CreateApplicationRequest extends ApplicationFieldsRequest {

        @NotBlank(message = "validation.appCode.required")
        @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "validation.appCode.pattern")
        private String appCode;

        public SaveApplicationCommand toCommand() {
            return super.toCommand(appCode.trim(), 0);
        }
    }

    @Getter
    @Setter
    public static class UpdateApplicationRequest extends ApplicationFieldsRequest {

        @Min(value = 1, message = "validation.version.positive")
        private long version;

        public SaveApplicationCommand toCommand(String appCode) {
            return super.toCommand(appCode, version);
        }
    }

    @Getter
    @Setter
    public abstract static class ApplicationFieldsRequest {

        @NotBlank(message = "validation.appName.required")
        @Size(max = 128, message = "validation.appName.size")
        private String name;
        @NotBlank(message = "validation.entryUrl.required")
        @Size(max = 512, message = "validation.entryUrl.size")
        private String entryUrl;
        @NotBlank(message = "validation.callback.required")
        @Size(max = 512, message = "validation.callback.size")
        private String ssoCallbackUrl;
        @NotBlank(message = "validation.permissionUrl.required")
        @Size(max = 512, message = "validation.permissionUrl.size")
        private String permissionIframeUrl;
        @NotBlank(message = "validation.notifyUrl.required")
        @Size(max = 512, message = "validation.notifyUrl.size")
        private String notifyBaseUrl;
        @Size(max = 64, message = "validation.tenant.size")
        private String defaultTenantCode;
        @NotEmpty(message = "validation.protocol.notEmpty")
        @Size(max = 3, message = "validation.protocol.size")
        private List<@NotNull(message = "validation.protocol.required")
                @Pattern(regexp = "sso|admin_ticket|user_sync", message = "validation.protocol.supported") String>
                protocolCapabilities;

        protected SaveApplicationCommand toCommand(String appCode, long version) {
            if (new java.util.HashSet<>(protocolCapabilities).size() != protocolCapabilities.size()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "VALIDATION_FAILED", "error.validation",
                        Collections.singletonMap("protocolCapabilities", "validation.protocol.unique"));
            }
            String protocols = protocolCapabilities.stream()
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(","));
            return new SaveApplicationCommand(
                    appCode,
                    name.trim(),
                    entryUrl.trim(),
                    ssoCallbackUrl.trim(),
                    permissionIframeUrl.trim(),
                    notifyBaseUrl.trim(),
                    defaultTenantCode == null ? null : defaultTenantCode.trim(),
                    protocols,
                    null,
                    version);
        }
    }

    @Getter
    @Setter
    public static class ChangeApplicationStatusRequest extends VersionedReasonRequest {

        @NotNull(message = "validation.status.required")
        @Pattern(regexp = "enabled|disabled", message = "validation.status.pattern")
        private String status;
    }

    @Getter
    @Setter
    public static class ChangeApplicationAccessRequest {

        @NotNull(message = "validation.status.required")
        @Pattern(regexp = "enabled|disabled", message = "validation.status.pattern")
        private String status;
        @NotNull(message = "validation.version.required")
        @Min(value = 0, message = "validation.version.nonnegative")
        private Long version;
        @NotBlank(message = "validation.reason.required")
        @Size(max = 256, message = "validation.reason.size")
        private String reason;
    }

    @Getter
    @Setter
    public static class VersionedReasonRequest {

        @Min(value = 1, message = "validation.version.positive")
        private long version;
        @NotBlank(message = "validation.reason.required")
        @Size(max = 256, message = "validation.reason.size")
        private String reason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationResponse {

        String appCode;
        String name;
        String entryUrl;
        String ssoCallbackUrl;
        String permissionIframeUrl;
        String notifyBaseUrl;
        String defaultTenantCode;
        String status;
        long version;
        int secretVersion;
        String secretState;
        List<String> protocolCapabilities;
        Instant createdAt;
        Instant updatedAt;

        public static ApplicationResponse from(AccountApplication application) {
            List<String> protocols = application.getProtocolCapabilities() == null
                    ? Collections.emptyList()
                    : Arrays.stream(application.getProtocolCapabilities().split(","))
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toList());
            return new ApplicationResponse(
                    application.getAppCode(), application.getName(), application.getEntryUrl(),
                    application.getSsoCallbackUrl(), application.getPermissionIframeUrl(),
                    application.getNotifyBaseUrl(), application.getDefaultTenantCode(),
                    application.getStatus(), application.getVersion(), application.getSecretVersion(),
                    application.getSecretState(), protocols, application.getCreatedAt(), application.getUpdatedAt());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationSecretResponse {

        ApplicationResponse application;
        String secret;

        public static ApplicationSecretResponse from(
                ManagementApplicationWriteService.CreatedApplication created) {
            return new ApplicationSecretResponse(
                    ApplicationResponse.from(created.getApplication()), created.getSecret());
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationAccessResponse {

        String userId;
        String appCode;
        String applicationName;
        String applicationStatus;
        String desiredStatus;
        long version;
        String integrationStatus;
        String syncCommandId;
        Instant updatedAt;

        public static ApplicationAccessResponse from(ApplicationAccess access, AccountApplication application) {
            return new ApplicationAccessResponse(
                    access.getUserId(), access.getAppCode(),
                    application == null ? access.getAppCode() : application.getName(),
                    application == null ? "disabled" : application.getStatus(),
                    access.getDesiredStatus(), access.getVersion(), access.getIntegrationStatus(),
                    access.getSyncCommandId(), access.getUpdatedAt());
        }
    }
}
