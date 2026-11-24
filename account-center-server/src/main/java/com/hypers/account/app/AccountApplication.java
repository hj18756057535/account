package com.hypers.account.app;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Account Center 注册应用实体。 */
@Getter
@Setter
@NoArgsConstructor
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
    private String secretState;
    private Long version;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public AccountApplication(String appCode, String name, String entryUrl, String ssoCallbackUrl,
                              String permissionIframeUrl, String notifyBaseUrl, String secret,
                              String defaultTenantCode) {
        this(appCode, name, entryUrl, ssoCallbackUrl, permissionIframeUrl, notifyBaseUrl, secret,
                defaultTenantCode, "enabled", 1, "active", 1L,
                null, null, null, null);
    }

    public AccountApplication(String appCode, String name, String entryUrl, String ssoCallbackUrl,
                              String permissionIframeUrl, String notifyBaseUrl, String secret,
                              String defaultTenantCode, String status, Integer secretVersion,
                              String createdBy, String updatedBy,
                              Instant createdAt, Instant updatedAt) {
        this(appCode, name, entryUrl, ssoCallbackUrl, permissionIframeUrl, notifyBaseUrl, secret,
                defaultTenantCode, status, secretVersion, "active", 1L,
                createdBy, updatedBy, createdAt, updatedAt);
    }

    public AccountApplication(String appCode, String name, String entryUrl, String ssoCallbackUrl,
                              String permissionIframeUrl, String notifyBaseUrl, String secret,
                              String defaultTenantCode, String status, Integer secretVersion,
                              String secretState, Long version,
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
        this.secretState = secretState;
        this.version = version;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean hasSsoConfiguration() {
        return ssoCallbackUrl != null && !ssoCallbackUrl.isBlank();
    }

    public boolean hasProviderConfiguration() {
        return notifyBaseUrl != null && !notifyBaseUrl.isBlank();
    }

    /** 可用功能由地址配置推导，只读展示，不保存另一份开关。 */
    public String getProtocolCapabilities() {
        List<String> available = new ArrayList<>();
        if (hasSsoConfiguration()) available.add("sso");
        if (permissionIframeUrl != null && !permissionIframeUrl.isBlank()) available.add("admin_ticket");
        if (hasProviderConfiguration()) available.addAll(List.of("directory", "user_sync", "menu_permission_v1"));
        return String.join(",", available);
    }
}
