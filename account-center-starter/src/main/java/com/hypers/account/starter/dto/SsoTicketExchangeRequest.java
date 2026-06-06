package com.hypers.account.starter.dto;

/**
 * SSO ticket 兑换请求。
 * 应用后端收到 SSO callback 后，用 code 向 Account Center 兑换用户信息。
 */
public class SsoTicketExchangeRequest {

    private String appCode;
    private String code;

    public SsoTicketExchangeRequest() {
    }

    public SsoTicketExchangeRequest(String appCode, String code) {
        this.appCode = appCode;
        this.code = code;
    }

    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
