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
}
