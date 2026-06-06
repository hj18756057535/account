package com.hypers.account.auth;

import com.hypers.account.mapper.AdminRoleMapper;
import java.util.List;

/**
 * 管理端权限服务。
 * 校验用户是否拥有 ACCOUNT_ADMIN 或 ACCOUNT_AUDITOR 角色。
 */
public class AdminAuthorizationService {

    private final AdminRoleMapper adminRoleMapper;

    public AdminAuthorizationService(AdminRoleMapper adminRoleMapper) {
        this.adminRoleMapper = adminRoleMapper;
    }

    /** 检查用户是否拥有指定角色 */
    public boolean hasRole(String userId, String roleCode) {
        List<String> roles = adminRoleMapper.selectRolesByUserId(userId);
        return roles.contains(roleCode);
    }

    /** 检查用户是否为管理员 */
    public boolean isAdmin(String userId) {
        return hasRole(userId, "ACCOUNT_ADMIN");
    }

    /** 检查用户是否为审计员 */
    public boolean isAuditor(String userId) {
        return hasRole(userId, "ACCOUNT_AUDITOR");
    }
}
