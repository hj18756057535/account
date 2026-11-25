package com.hypers.account.app.model;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 应用内用户身份与最近一次成功下发的状态。 */
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
