package com.hypers.account.sso;

public class SsoUserPayload {

    private final String externalUserId;
    private final String account;
    private final String email;
    private final String name;
    private final String phone;
    private final String tenantCode;

    public SsoUserPayload(String externalUserId, String account, String email, String name, String phone, String tenantCode) {
        this.externalUserId = externalUserId;
        this.account = account;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.tenantCode = tenantCode;
    }

    public String getExternalUserId() {
        return externalUserId;
    }

    public String getAccount() {
        return account;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getTenantCode() {
        return tenantCode;
    }
}
