package com.hypers.account.app.http;

/**
 * Account Center 向应用同步用户数据的请求体。
 * 授权时 enabled=true，取消授权或禁用时 enabled=false。
 */
public class ApplicationSyncRequest {

    private String appCode;
    private String tenantCode;
    private String externalUserId;
    private String account;
    private String email;
    private String name;
    private String phone;
    private boolean enabled;

    public ApplicationSyncRequest() {
    }

    public ApplicationSyncRequest(String appCode, String tenantCode, String externalUserId,
                                  String account, String email, String name, String phone, boolean enabled) {
        this.appCode = appCode;
        this.tenantCode = tenantCode;
        this.externalUserId = externalUserId;
        this.account = account;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.enabled = enabled;
    }

    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
    public String getExternalUserId() { return externalUserId; }
    public void setExternalUserId(String externalUserId) { this.externalUserId = externalUserId; }
    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
