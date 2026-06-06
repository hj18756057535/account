package com.hypers.account.audit;

import java.time.Instant;

/**
 * 操作审计日志实体。
 * 记录 Account Center 所有关键管理操作。
 */
public class AuditLog {

    private String id;
    private String operatorId;
    private String operationType;
    private String targetType;
    private String targetId;
    private String detail;
    private Instant createdAt;

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
