package com.hypers.account.audit;

import com.hypers.account.mapper.AuditLogMapper;
import com.hypers.account.app.PageResult;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;

/**
 * 审计日志服务。
 * 提供日志记录和查询能力，供各业务操作调用。
 */
@RequiredArgsConstructor
public class AuditLogService {

    public static final String TRACE_CONTEXT_KEY = "account.traceId";

    private final AuditLogMapper auditLogMapper;

    /** 记录一条审计日志 */
    public void log(String operatorId, String operationType, String targetType, String targetId, String detail) {
        AuditLog auditLog = new AuditLog(
                UUID.randomUUID().toString(),
                operatorId, operationType, targetType, targetId, detail);
        auditLog.setTraceId(MDC.get(TRACE_CONTEXT_KEY));
        auditLog.setOutcome("success");
        auditLogMapper.insert(auditLog);
    }

    /** 按条件查询审计日志 */
    public List<AuditLog> search(String operatorId, String operationType, String targetType, String targetId) {
        return auditLogMapper.selectByCondition(operatorId, operationType, targetType, targetId);
    }

    public PageResult<AuditLog> findEvents(AuditPageQuery query) {
        long total = auditLogMapper.countEvents(query);
        List<AuditLog> items = total == 0 ? java.util.Collections.emptyList()
                : auditLogMapper.selectEvents(query);
        return new PageResult<>(items, query.getPage(), query.getSize(), total);
    }

    public AuditLog findEvent(String id) {
        return auditLogMapper.selectEvent(id);
    }
}
