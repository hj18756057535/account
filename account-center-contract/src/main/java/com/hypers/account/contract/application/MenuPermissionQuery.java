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
public class MenuPermissionQuery {

    private String protocolVersion;
    private String appCode;
    private String globalUserId;
    private String localUserId;
    private String account;
    private String locale;
}
