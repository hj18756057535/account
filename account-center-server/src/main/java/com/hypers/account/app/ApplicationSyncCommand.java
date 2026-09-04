package com.hypers.account.app;

import java.time.Instant;
import lombok.Value;

@Value
public class ApplicationSyncCommand {

    String id;
    String idempotencyKey;
    String userId;
    String appCode;
    String desiredStatus;
    long syncVersion;
    String status;
    int attempts;
    String traceId;
    Instant createdAt;
}
