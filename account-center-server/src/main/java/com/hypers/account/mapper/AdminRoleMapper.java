package com.hypers.account.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 管理员角色 Mapper。
 * 管理 Account Center 管理后台的角色分配。
 */
@Mapper
public interface AdminRoleMapper {

    /** 查询用户的角色列表 */
    List<String> selectRolesByUserId(@Param("userId") String userId);

    /** 新增角色 */
    void insert(@Param("userId") String userId, @Param("roleCode") String roleCode);
}
