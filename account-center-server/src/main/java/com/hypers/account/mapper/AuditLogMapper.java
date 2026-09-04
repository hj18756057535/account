package com.hypers.account.mapper;

import com.hypers.account.audit.AuditLog;
import com.hypers.account.audit.AuditPageQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 操作审计日志 Mapper。
 * 记录用户、应用、授权、同步、SSO、管理 ticket 相关的关键操作。
 */
@Mapper
public interface AuditLogMapper {

    long countEvents(@Param("query") AuditPageQuery query);

    List<AuditLog> selectEvents(@Param("query") AuditPageQuery query);

    AuditLog selectEvent(@Param("id") String id);

    /** 新增审计日志 */
    void insert(@Param("log") AuditLog log);

    /** 按条件搜索审计日志（支持操作人、操作类型、目标类型、目标 ID、时间范围） */
    List<AuditLog> selectByCondition(@Param("operatorId") String operatorId,
                                     @Param("operationType") String operationType,
                                     @Param("targetType") String targetType,
                                     @Param("targetId") String targetId);
}
