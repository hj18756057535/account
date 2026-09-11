package com.hypers.account.web.management;

import com.hypers.account.app.ApplicationMenuPermissionService;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.contract.application.MenuPermissionNode;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import com.hypers.account.web.ApiMessages;
import com.hypers.account.web.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "account.console-api.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ManagementMenuPermissionController {

    private final ApplicationMenuPermissionService service;
    private final IdempotencyService idempotency;
    private final ApiMessages messages;

    @GetMapping("/api/users/{userId}/applications/{appCode}/menu-permissions")
    public MenuPermissionResponse query(@PathVariable String userId,
                                        @PathVariable String appCode,
                                        HttpServletRequest request,
                                        HttpSession session) {
        validatePath(userId, appCode);
        return MenuPermissionResponse.from(service.query(
                userId, appCode, locale(request), operatorId(session)));
    }

    @PutMapping("/api/users/{userId}/applications/{appCode}/menu-permissions")
    public MenuPermissionResponse replace(
            @PathVariable String userId,
            @PathVariable String appCode,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReplaceMenuPermissionRequest body,
            HttpServletRequest request,
            HttpSession session) {
        validatePath(userId, appCode);
        String operatorId = operatorId(session);
        String path = "/api/users/" + userId + "/applications/" + appCode + "/menu-permissions";
        return idempotency.execute(operatorId, "PUT", path, idempotencyKey, body,
                HttpStatus.OK.value(), MenuPermissionResponse.class,
                () -> MenuPermissionResponse.from(service.replace(
                        userId, appCode, body.getExpectedAccessVersion(), body.getExpectedCatalogRevision(),
                        body.getExpectedPermissionRevision(), body.getSelectedCodes(), locale(request),
                        idempotencyKey, operatorId)));
    }

    private String locale(HttpServletRequest request) {
        return "en".equals(messages.locale(request).getLanguage()) ? "en-US" : "zh-CN";
    }

    private String operatorId(HttpSession session) {
        return ((AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY)).getUserId();
    }

    private void validatePath(String userId, String appCode) {
        if (userId == null || userId.isBlank() || userId.length() > 64) {
            throw validation("userId", "validation.userId");
        }
        if (appCode == null || !appCode.matches("[a-z][a-z0-9-]{0,63}")) {
            throw validation("appCode", "validation.appCode.format");
        }
    }

    private ApiException validation(String field, String key) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "error.validation",
                Collections.singletonMap(field, key));
    }

    @Getter
    @Setter
    public static class ReplaceMenuPermissionRequest {

        @NotNull(message = "validation.version.required")
        @Min(value = 1, message = "validation.version.positive")
        @Max(value = 9007199254740991L, message = "validation.version.positive")
        private Long expectedAccessVersion;

        @NotBlank(message = "validation.menuPermissionRevision")
        @Size(max = 256, message = "validation.menuPermissionRevision")
        private String expectedCatalogRevision;

        @NotBlank(message = "validation.menuPermissionRevision")
        @Size(max = 256, message = "validation.menuPermissionRevision")
        private String expectedPermissionRevision;

        @NotNull(message = "validation.menuPermissionCodes")
        @Size(max = 5000, message = "validation.menuPermissionCodes")
        private List<@NotBlank(message = "validation.menuPermissionCodes")
                @Size(max = 128, message = "validation.menuPermissionCodes") String> selectedCodes;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuPermissionResponse {
        private long accessVersion;
        private String catalogRevision;
        private String permissionRevision;
        private String assignmentMode;
        private List<MenuPermissionNode> nodes;
        private List<String> selectedCodes;
        private List<String> inheritedCodes;

        static MenuPermissionResponse from(ApplicationMenuPermissionService.Result result) {
            MenuPermissionSnapshot snapshot = result.getSnapshot();
            return new MenuPermissionResponse(result.getAccessVersion(), snapshot.getCatalogRevision(),
                    snapshot.getPermissionRevision(), snapshot.getAssignmentMode(), snapshot.getNodes(),
                    snapshot.getSelectedCodes(), snapshot.getInheritedCodes());
        }
    }
}
