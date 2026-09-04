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
            throw validation("status", "应用状态只能是 enabled 或 disabled");
        }
        String normalizedQuery = normalize(query);
        if (normalizedQuery != null && normalizedQuery.length() > 128) {
            throw validation("query", "搜索内容长度不能超过 128 个字符");
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
                    "APPLICATION_NOT_FOUND", "未找到指定应用");
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
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "未找到指定用户");
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
            throw validation("appCode", "应用编码格式不正确");
        }
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isEmpty() || userId.length() > 64) {
            throw validation("userId", "用户标识格式不正确");
        }
    }

    private ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED", "请求字段校验失败",
                Collections.singletonMap(field, message));
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Getter
    @Setter
    public static class CreateApplicationRequest extends ApplicationFieldsRequest {

        @NotBlank(message = "应用编码不能为空")
        @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "应用编码只能包含小写字母、数字和连字符")
        private String appCode;

        public SaveApplicationCommand toCommand() {
            return super.toCommand(appCode.trim(), 0);
        }
    }

    @Getter
    @Setter
    public static class UpdateApplicationRequest extends ApplicationFieldsRequest {

        @Min(value = 1, message = "资源版本必须大于 0")
        private long version;

        public SaveApplicationCommand toCommand(String appCode) {
            return super.toCommand(appCode, version);
        }
    }

    @Getter
    @Setter
    public abstract static class ApplicationFieldsRequest {

        @NotBlank(message = "应用名称不能为空")
        @Size(max = 128, message = "应用名称长度不能超过 128 个字符")
        private String name;
        @NotBlank(message = "入口地址不能为空")
        @Size(max = 512, message = "入口地址长度不能超过 512 个字符")
        private String entryUrl;
        @NotBlank(message = "SSO 回调地址不能为空")
        @Size(max = 512, message = "SSO 回调地址长度不能超过 512 个字符")
        private String ssoCallbackUrl;
        @NotBlank(message = "授权页地址不能为空")
        @Size(max = 512, message = "授权页地址长度不能超过 512 个字符")
        private String permissionIframeUrl;
        @NotBlank(message = "同步通知地址不能为空")
        @Size(max = 512, message = "同步通知地址长度不能超过 512 个字符")
        private String notifyBaseUrl;
        @Size(max = 64, message = "默认租户编码长度不能超过 64 个字符")
        private String defaultTenantCode;
        @NotEmpty(message = "至少选择一项协议能力")
        @Size(max = 3, message = "协议能力数量不能超过 3")
        private List<@NotNull(message = "协议能力不能为空")
                @Pattern(regexp = "sso|admin_ticket|user_sync", message = "协议能力不受支持") String>
                protocolCapabilities;

        protected SaveApplicationCommand toCommand(String appCode, long version) {
            if (new java.util.HashSet<>(protocolCapabilities).size() != protocolCapabilities.size()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "VALIDATION_FAILED", "请求字段校验失败",
                        Collections.singletonMap("protocolCapabilities", "协议能力不能重复"));
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

        @NotNull(message = "状态不能为空")
        @Pattern(regexp = "enabled|disabled", message = "状态只能是 enabled 或 disabled")
        private String status;
    }

    @Getter
    @Setter
    public static class ChangeApplicationAccessRequest {

        @NotNull(message = "状态不能为空")
        @Pattern(regexp = "enabled|disabled", message = "状态只能是 enabled 或 disabled")
        private String status;
        @NotNull(message = "资源版本不能为空")
        @Min(value = 0, message = "资源版本不能小于 0")
        private Long version;
        @NotBlank(message = "变更原因不能为空")
        @Size(max = 256, message = "变更原因不能超过 256 个字符")
        private String reason;
    }

    @Getter
    @Setter
    public static class VersionedReasonRequest {

        @Min(value = 1, message = "资源版本必须大于 0")
        private long version;
        @NotBlank(message = "变更原因不能为空")
        @Size(max = 256, message = "变更原因不能超过 256 个字符")
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
