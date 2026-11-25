package com.hypers.account.app.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 管理端新建或更新应用的入参，expectedVersion 用于乐观锁。 */
@Getter
@RequiredArgsConstructor
public class SaveApplicationCommand {

    private final String appCode;
    private final String name;
    private final String entryUrl;
    private final String ssoCallbackUrl;
    private final String permissionIframeUrl;
    private final String notifyBaseUrl;
    private final String defaultTenantCode;
    private final String secret;
    private final long expectedVersion;
}
