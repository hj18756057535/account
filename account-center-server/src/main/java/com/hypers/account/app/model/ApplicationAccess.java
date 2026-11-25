package com.hypers.account.app.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 用户对单个应用的准入状态：期望状态、下发状态、版本与最近同步信息。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationAccess {

    String userId;
    String appCode;
    String desiredStatus;
    long version;
    String integrationStatus;
    String syncCommandId;
    Instant updatedAt;
    String appliedStatus;
    Long appliedVersion;
    Instant lastSyncedAt;
    String lastErrorCode;
    boolean retryable;

    public ApplicationAccess(String userId, String appCode, String desiredStatus, long version,
                             String integrationStatus, String syncCommandId, Instant updatedAt) {
        this(userId, appCode, desiredStatus, version, integrationStatus, syncCommandId, updatedAt,
                null, null, null, null, false);
    }
}
