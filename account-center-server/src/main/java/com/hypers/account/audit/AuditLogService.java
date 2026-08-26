package com.hypers.account.audit;

import com.hypers.account.mapper.AuditLogMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * 审计日志服务。
 * 提供日志记录和查询能力，供各业务操作调用。
 */
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /** 记录一条审计日志 */
    public void log(String operatorId, String operationType, String targetType, String targetId, String detail) {
        AuditLog auditLog = new AuditLog(
                UUID.randomUUID().toString(),
                operatorId, operationType, targetType, targetId, detail);
        auditLogMapper.insert(auditLog);
    }

    /** 按条件查询审计日志 */
    public List<AuditLog> search(String operatorId, String operationType, String targetType, String targetId) {
        return auditLogMapper.selectByCondition(operatorId, operationType, targetType, targetId);
    }
}
