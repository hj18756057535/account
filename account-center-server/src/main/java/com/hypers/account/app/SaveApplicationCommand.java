package com.hypers.account.app;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
    private final String protocolCapabilities;
    private final String secret;
    private final long expectedVersion;
}
