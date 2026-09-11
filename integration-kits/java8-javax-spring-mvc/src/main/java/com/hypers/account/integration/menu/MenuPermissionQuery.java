package com.hypers.account.integration.menu;

import lombok.Data;

@Data
public class MenuPermissionQuery {

    private String protocolVersion;
    private String appCode;
    private String globalUserId;
    private String localUserId;
    private String account;
    private String locale;

}
