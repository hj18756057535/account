package com.hypers.account.app;

import java.time.Instant;

public class AccountApplication {

    private String appCode;
    private String name;
    private String entryUrl;
    private String ssoCallbackUrl;
    private String permissionIframeUrl;
    private String notifyBaseUrl;
    private String secret;
    private String defaultTenantCode;
    private String status;
    private Integer secretVersion;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public AccountApplication() {
    }

    public AccountApplication(String appCode, String name, String entryUrl, String ssoCallbackUrl,
                              String permissionIframeUrl, String notifyBaseUrl, String secret,
                              String defaultTenantCode) {
        this(appCode, name, entryUrl, ssoCallbackUrl, permissionIframeUrl, notifyBaseUrl, secret,
                defaultTenantCode, "enabled", 1, null, null, null, null);
    }

    public AccountApplication(String appCode, String name, String entryUrl, String ssoCallbackUrl,
                              String permissionIframeUrl, String notifyBaseUrl, String secret,
                              String defaultTenantCode, String status, Integer secretVersion,
                              String createdBy, String updatedBy,
                              Instant createdAt, Instant updatedAt) {
        this.appCode = appCode;
        this.name = name;
        this.entryUrl = entryUrl;
        this.ssoCallbackUrl = ssoCallbackUrl;
        this.permissionIframeUrl = permissionIframeUrl;
        this.notifyBaseUrl = notifyBaseUrl;
        this.secret = secret;
        this.defaultTenantCode = defaultTenantCode;
        this.status = status;
        this.secretVersion = secretVersion;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEntryUrl() {
        return entryUrl;
    }

    public void setEntryUrl(String entryUrl) {
        this.entryUrl = entryUrl;
    }

    public String getSsoCallbackUrl() {
        return ssoCallbackUrl;
    }

    public void setSsoCallbackUrl(String ssoCallbackUrl) {
        this.ssoCallbackUrl = ssoCallbackUrl;
    }

    public String getPermissionIframeUrl() {
        return permissionIframeUrl;
    }

    public void setPermissionIframeUrl(String permissionIframeUrl) {
        this.permissionIframeUrl = permissionIframeUrl;
    }

    public String getNotifyBaseUrl() {
        return notifyBaseUrl;
    }

    public void setNotifyBaseUrl(String notifyBaseUrl) {
        this.notifyBaseUrl = notifyBaseUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getDefaultTenantCode() {
        return defaultTenantCode;
    }

    public void setDefaultTenantCode(String defaultTenantCode) {
        this.defaultTenantCode = defaultTenantCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSecretVersion() {
        return secretVersion;
    }

    public void setSecretVersion(Integer secretVersion) {
        this.secretVersion = secretVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
