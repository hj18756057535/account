package com.hypers.account.web.management;

import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.PageResult;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.app.UserPageQuery;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.web.AuthController;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@ConditionalOnProperty(name = "account.console-api.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ManagementUserController {

    private static final Set<String> USER_STATUSES = new java.util.HashSet<>(Arrays.asList("enabled", "disabled"));
    private static final Set<String> SORT_FIELDS = new java.util.HashSet<>(Arrays.asList("createdAt", "account", "name"));
    private static final Set<String> SORT_DIRECTIONS = new java.util.HashSet<>(Arrays.asList("asc", "desc"));

    private final AccountDirectoryService directoryService;
    private final ManagementUserWriteService writeService;
    private final IdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateUserRequest request,
            HttpSession session) {
        String operatorId = operatorId(session);
        UserResponse response = idempotencyService.execute(
                operatorId,
                "POST",
                "/api/users",
                idempotencyKey,
                request,
                HttpStatus.CREATED.value(),
                UserResponse.class,
                () -> UserResponse.from(writeService.create(request.toCommand(), operatorId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public UserPageResponse findUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        UserPageQuery pageQuery = toQuery(page, size, query, status, sort);
        PageResult<AccountUser> result = directoryService.findUsersPage(pageQuery);
        List<UserResponse> items = result.getItems().stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());
        return new UserPageResponse(items, result.getPage(), result.getSize(), result.getTotal());
    }

    @GetMapping("/{userId}")
    public UserResponse getUser(@PathVariable String userId) {
        validateUserId(userId);
        try {
            return UserResponse.from(directoryService.getUser(userId));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "未找到指定用户");
        }
    }

    @PutMapping("/{userId}")
    public UserResponse updateUser(
            @PathVariable String userId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody UpdateUserRequest request,
            HttpSession session) {
        validateUserId(userId);
        String operatorId = operatorId(session);
        return idempotencyService.execute(
                operatorId,
                "PUT",
                "/api/users/" + userId,
                idempotencyKey,
                request,
                HttpStatus.OK.value(),
                UserResponse.class,
                () -> UserResponse.from(writeService.update(
                        userId, request.toCommand(), request.getVersion(), operatorId)));
    }

    @PutMapping("/{userId}/status")
    public UserResponse changeUserStatus(
            @PathVariable String userId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ChangeUserStatusRequest request,
            HttpSession session) {
        validateUserId(userId);
        String operatorId = operatorId(session);
        return idempotencyService.execute(
                operatorId,
                "PUT",
                "/api/users/" + userId + "/status",
                idempotencyKey,
                request,
                HttpStatus.OK.value(),
                UserResponse.class,
                () -> UserResponse.from(writeService.changeStatus(
                        userId,
                        request.getStatus(),
                        request.getVersion(),
                        request.getReason(),
                        operatorId)));
    }

    private UserPageQuery toQuery(int page,
                                  int size,
                                  String keyword,
                                  String status,
                                  String sort) {
        if (page < 1) {
            throw validation("page", "页码必须从 1 开始");
        }
        if (size < 1 || size > 100) {
            throw validation("size", "每页数量必须在 1 到 100 之间");
        }
        String normalizedStatus = normalize(status);
        if (normalizedStatus != null && !USER_STATUSES.contains(normalizedStatus)) {
            throw validation("status", "用户状态只能是 enabled 或 disabled");
        }
        String[] sortParts = sort == null ? new String[0] : sort.split(",", -1);
        if (sortParts.length != 2 || !SORT_FIELDS.contains(sortParts[0])
                || !SORT_DIRECTIONS.contains(sortParts[1].toLowerCase(Locale.ROOT))) {
            throw validation("sort", "排序格式不受支持");
        }
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword != null && normalizedKeyword.length() > 128) {
            throw validation("query", "搜索内容长度不能超过 128 个字符");
        }
        return new UserPageQuery(
                page,
                size,
                normalizedKeyword,
                normalizedStatus,
                sortParts[0],
                sortParts[1].toLowerCase(Locale.ROOT));
    }

    private ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_FAILED", "请求字段校验失败", java.util.Collections.singletonMap(field, message));
    }

    private void validateUserId(String userId) {
        if (userId == null || userId.isEmpty() || userId.length() > 64) {
            throw validation("userId", "用户标识格式不正确");
        }
    }

    private String operatorId(HttpSession session) {
        AccountSessionUser user = (AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY);
        return user.getUserId();
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @Value
    public static class UserPageResponse {

        List<UserResponse> items;
        int page;
        int size;
        long total;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserResponse {

        String id;
        String account;
        String email;
        String name;
        String phone;
        String status;
        long version;
        Instant createdAt;
        Instant updatedAt;

        public static UserResponse from(AccountUser user) {
            return new UserResponse(
                    user.getId(),
                    user.getAccount(),
                    user.getEmail(),
                    user.getName(),
                    user.getPhone(),
                    user.getStatus(),
                    user.getVersion(),
                    user.getCreatedAt(),
                    user.getUpdatedAt());
        }
    }

    @Getter
    @Setter
    public static class CreateUserRequest {

        @NotBlank(message = "账号不能为空")
        @Size(max = 128, message = "账号长度不能超过 128 个字符")
        private String account;
        @NotBlank(message = "邮箱不能为空")
        @Email(message = "邮箱格式不正确")
        @Size(max = 255, message = "邮箱长度不能超过 255 个字符")
        private String email;
        @NotBlank(message = "姓名不能为空")
        @Size(max = 128, message = "姓名长度不能超过 128 个字符")
        private String name;
        @NotBlank(message = "手机号不能为空")
        @Size(max = 64, message = "手机号长度不能超过 64 个字符")
        private String phone;

        public SaveUserCommand toCommand() {
            return new SaveUserCommand(account.trim(), email.trim(), name.trim(), phone.trim());
        }
    }

    @Getter
    @Setter
    public static class UpdateUserRequest extends CreateUserRequest {

        @Min(value = 1, message = "资源版本必须大于 0")
        private long version;
    }

    @Getter
    @Setter
    public static class ChangeUserStatusRequest {

        @NotNull(message = "状态不能为空")
        @Pattern(regexp = "enabled|disabled", message = "状态只能是 enabled 或 disabled")
        private String status;
        @Min(value = 1, message = "资源版本必须大于 0")
        private long version;
        @NotBlank(message = "状态变更原因不能为空")
        @Size(max = 256, message = "状态变更原因不能超过 256 个字符")
        private String reason;
    }
}
