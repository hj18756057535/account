package com.hypers.account.web.management;

import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.PageResult;
import com.hypers.account.app.UserPageQuery;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
        if (userId == null || userId.isEmpty() || userId.length() > 64) {
            throw validation("userId", "用户标识格式不正确");
        }
        try {
            return UserResponse.from(directoryService.getUser(userId));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "未找到指定用户");
        }
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

    @Value
    public static class UserResponse {

        String id;
        String account;
        String email;
        String name;
        String phone;
        String status;
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
                    user.getCreatedAt(),
                    user.getUpdatedAt());
        }
    }
}
