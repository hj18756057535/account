package com.hypers.account.starter.dto;

/**
 * SSO ticket 兑换响应。
 * Account Center 返回用户快照信息，应用据此创建或匹配本地用户并签发本地 token。
 */
public class SsoTicketExchangeResponse {

    private String externalUserId;
    private String account;
    private String email;
    private String name;
    private String phone;
    private String tenantCode;

    public SsoTicketExchangeResponse() {
    }

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
    public String getTenantCode() { return tenantCode; }
    public void setTenantCode(String tenantCode) { this.tenantCode = tenantCode; }
}
