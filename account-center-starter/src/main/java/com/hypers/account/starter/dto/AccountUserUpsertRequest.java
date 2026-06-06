package com.hypers.account.starter.dto;

/**
 * Account Center 同步用户 upsert 请求。
 * Account Center 授权用户时调用应用的 /account-sso/internal/users/upsert。
 */
public class AccountUserUpsertRequest {

    private String appCode;
    private String tenantCode;
    private String externalUserId;
    private String account;
    private String email;
    private String name;
    private String phone;
    private boolean enabled;

    public AccountUserUpsertRequest() {
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
