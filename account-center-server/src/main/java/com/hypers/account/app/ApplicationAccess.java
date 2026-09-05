package com.hypers.account.app;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
