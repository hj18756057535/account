package com.hypers.account.contract.application;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountUserDesiredState {

    private String appCode;
    private String globalUserId;
    private String localUserId;
    private String account;
    private String displayName;
    private String email;
    private String phone;
    private String tenantCode;
    private String desiredStatus;
    private long syncVersion;
    private Instant occurredAt;
}
