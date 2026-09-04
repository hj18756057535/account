package com.hypers.account.auth;

import com.hypers.account.mapper.AdminRoleMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * 管理端权限服务。
 * 校验用户是否拥有 ACCOUNT_ADMIN 或 ACCOUNT_AUDITOR 角色。
 */
@RequiredArgsConstructor
public class AdminAuthorizationService {

    public static final String ACCOUNT_ADMIN = "ACCOUNT_ADMIN";
    public static final String ACCOUNT_AUDITOR = "ACCOUNT_AUDITOR";
    public static final String USERS_READ = "users:read";
    public static final String USERS_WRITE = "users:write";
    public static final String APPLICATIONS_READ = "applications:read";
    public static final String APPLICATIONS_WRITE = "applications:write";
    public static final String ACCESS_READ = "application-access:read";
    public static final String ACCESS_WRITE = "application-access:write";
    public static final String AUDIT_READ = "audit:read";

    private final AdminRoleMapper adminRoleMapper;

    /** 检查用户是否拥有指定角色 */
    public boolean hasRole(String userId, String roleCode) {
        return findRoles(userId).contains(roleCode);
    }

    /** 检查用户是否为管理员 */
    public boolean isAdmin(String userId) {
        return hasRole(userId, ACCOUNT_ADMIN);
    }

    /** 检查用户是否为审计员 */
    public boolean isAuditor(String userId) {
        return hasRole(userId, ACCOUNT_AUDITOR);
    }

    public boolean canReadUsers(String userId) {
        List<String> roles = findRoles(userId);
        return roles.contains(ACCOUNT_ADMIN) || roles.contains(ACCOUNT_AUDITOR);
    }

    public List<String> findRoles(String userId) {
        List<String> roles = adminRoleMapper.selectRolesByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> supportedRoles = roles.stream()
                .filter(role -> ACCOUNT_ADMIN.equals(role) || ACCOUNT_AUDITOR.equals(role))
                .collect(Collectors.toList());
        return Collections.unmodifiableList(supportedRoles);
    }

    public List<String> findCapabilities(List<String> roles) {
        Set<String> capabilities = new LinkedHashSet<>();
        if (roles.contains(ACCOUNT_ADMIN) || roles.contains(ACCOUNT_AUDITOR)) {
            capabilities.add(USERS_READ);
            capabilities.add(APPLICATIONS_READ);
            capabilities.add(ACCESS_READ);
            capabilities.add(AUDIT_READ);
        }
        if (roles.contains(ACCOUNT_ADMIN)) {
            capabilities.add(USERS_WRITE);
            capabilities.add(APPLICATIONS_WRITE);
            capabilities.add(ACCESS_WRITE);
        }
        return Collections.unmodifiableList(new ArrayList<>(capabilities));
    }

    public int countEnabledAdmins() {
        return adminRoleMapper.countEnabledAdmins();
    }

    public int lockAndCountEnabledAdmins() {
        return adminRoleMapper.lockEnabledAdminIds().size();
    }
}
