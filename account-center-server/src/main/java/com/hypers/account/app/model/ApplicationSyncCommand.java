package com.hypers.account.app.model;

import java.time.Instant;
import lombok.Value;

/** 一次用户状态下发的同步命令，按版本幂等。 */
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
