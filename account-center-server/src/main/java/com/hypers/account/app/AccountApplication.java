package com.hypers.account.app;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Account Center 注册应用实体。 */
@Getter
@Setter
@NoArgsConstructor
public class AccountApplication {

    public static final String DEFAULT_PROTOCOL_CAPABILITIES = "sso,admin_ticket,user_sync";

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
    private String protocolCapabilities;
    private Long version;
    private boolean managedSync;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;

    public AccountApplication(String appCode, String name, String entryUrl, String ssoCallbackUrl,
                              String permissionIframeUrl, String notifyBaseUrl, String secret,
                              String defaultTenantCode) {
        this(appCode, name, entryUrl, ssoCallbackUrl, permissionIframeUrl, notifyBaseUrl, secret,
                defaultTenantCode, "enabled", 1, "active", DEFAULT_PROTOCOL_CAPABILITIES, 1L,
                null, null, null, null);
    }

    public AccountApplication(String appCode, String name, String entryUrl, String ssoCallbackUrl,
                              String permissionIframeUrl, String notifyBaseUrl, String secret,
                              String defaultTenantCode, String status, Integer secretVersion,
                              String createdBy, String updatedBy,
                              Instant createdAt, Instant updatedAt) {
        this(appCode, name, entryUrl, ssoCallbackUrl, permissionIframeUrl, notifyBaseUrl, secret,
                defaultTenantCode, status, secretVersion, "active", DEFAULT_PROTOCOL_CAPABILITIES, 1L,
                createdBy, updatedBy, createdAt, updatedAt);
    }

    public AccountApplication(String appCode, String name, String entryUrl, String ssoCallbackUrl,
                              String permissionIframeUrl, String notifyBaseUrl, String secret,
                              String defaultTenantCode, String status, Integer secretVersion,
                              String secretState, String protocolCapabilities, Long version,
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
        this.protocolCapabilities = protocolCapabilities;
        this.version = version;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
