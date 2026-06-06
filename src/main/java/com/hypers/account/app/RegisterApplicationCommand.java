package com.hypers.account.app;

public class RegisterApplicationCommand {

    private final String appCode;
    private final String name;
    private final String entryUrl;
    private final String ssoCallbackUrl;
    private final String permissionIframeUrl;
    private final String notifyBaseUrl;
    private final String secret;
    private final String defaultTenantCode;

    public RegisterApplicationCommand(String appCode, String name, String entryUrl, String ssoCallbackUrl,
                                      String permissionIframeUrl, String notifyBaseUrl, String secret,
                                      String defaultTenantCode) {
        this.appCode = appCode;
        this.name = name;
        this.entryUrl = entryUrl;
        this.ssoCallbackUrl = ssoCallbackUrl;
        this.permissionIframeUrl = permissionIframeUrl;
        this.notifyBaseUrl = notifyBaseUrl;
        this.secret = secret;
        this.defaultTenantCode = defaultTenantCode;
    }

    public String getAppCode() {
        return appCode;
    }

    public String getName() {
        return name;
    }

    public String getEntryUrl() {
        return entryUrl;
    }

    public String getSsoCallbackUrl() {
        return ssoCallbackUrl;
    }

    public String getPermissionIframeUrl() {
        return permissionIframeUrl;
    }

    public String getNotifyBaseUrl() {
        return notifyBaseUrl;
    }

    public String getSecret() {
        return secret;
    }

    public String getDefaultTenantCode() {
        return defaultTenantCode;
    }
}
