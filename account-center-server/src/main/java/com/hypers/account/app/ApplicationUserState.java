package com.hypers.account.app;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationUserState {
    private String userId;
    private String appCode;
    private String localUserId;
    private String appliedStatus;
    private long appliedVersion;
    private Instant lastSyncedAt;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
