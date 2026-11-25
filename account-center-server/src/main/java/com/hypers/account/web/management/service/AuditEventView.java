package com.hypers.account.web.management.service;

import com.hypers.account.audit.AuditLog;
import java.time.Instant;
import java.util.Set;
import lombok.Value;

/**
 * 管理端展示用的审计事件视图。
 *
 * <p>只暴露管理对象的标识，非管理类事件不返回 targetId，避免把内部目标带出到接口。
 */
@Value
public class AuditEventView {

    String id;
    String operatorId;
    String operationType;
    String targetType;
    String targetId;
    String outcome;
    String traceId;
    Instant createdAt;

    public static AuditEventView from(AuditLog log) {
        boolean managementTarget = log.getTargetType() != null
                && Set.of("USER", "APPLICATION", "USER_APPLICATION").contains(log.getTargetType());
        String outcome = log.getOutcome();
        if (outcome == null || !Set.of("success", "failure").contains(outcome)) outcome = "unknown";
        return new AuditEventView(log.getId(), log.getOperatorId(), log.getOperationType(),
                log.getTargetType(), managementTarget ? log.getTargetId() : null,
                outcome, log.getTraceId(), log.getCreatedAt());
    }
}
