package com.hypers.account.audit;

import lombok.Value;

@Value
public class AuditPageQuery {
    int page;
    int size;
    String operatorId;
    String operationType;
    String targetType;
    String targetId;
    String traceId;

    public int getOffset() {
        return (page - 1) * size;
    }
}
