package com.hypers.account.contract.application;

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
public class AccountUserSyncResult {

    private String appCode;
    private String globalUserId;
    private String localUserId;
    private String appliedStatus;
    private long appliedVersion;
    private String resultCode;
}
