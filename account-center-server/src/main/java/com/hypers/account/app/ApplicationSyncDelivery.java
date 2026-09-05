package com.hypers.account.app;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationSyncDelivery {
    private String id;
    private String idempotencyKey;
    private String userId;
    private String appCode;
    private String desiredStatus;
    private long syncVersion;
    private String status;
    private int attempts;
    private Instant nextRetryAt;
    private String errorCode;
    private String traceId;
    private Instant createdAt;
    private Instant updatedAt;
    private String payloadJson;
    private String payloadHash;
    private String operatorId;
    private Long targetApplicationVersion;
    private String leaseToken;
    private Instant leaseExpiresAt;
}
