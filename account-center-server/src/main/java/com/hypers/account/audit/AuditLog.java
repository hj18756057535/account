package com.hypers.account.audit;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/**
 * 操作审计日志实体。
 * 记录 Account Center 所有关键管理操作。
 */
@Getter
@Setter
public class AuditLog {

    private String id;
    private String operatorId;
    private String operationType;
    private String targetType;
    private String targetId;
    private String detail;
    private Instant createdAt;
    private String traceId;
    private String outcome = "unknown";

    public AuditLog() {
    }

    public AuditLog(String id, String operatorId, String operationType,
                    String targetType, String targetId, String detail) {
        this.id = id;
        this.operatorId = operatorId;
        this.operationType = operationType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }

}
