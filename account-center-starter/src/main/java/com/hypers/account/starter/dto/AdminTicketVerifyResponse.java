package com.hypers.account.starter.dto;

/**
 * 管理 ticket 校验响应。
 * 校验成功后返回 appCode 和 userId，应用据此渲染 iframe 授权页面。
 */
public class AdminTicketVerifyResponse {

    private String appCode;
    private String userId;

    public AdminTicketVerifyResponse() {
    }

    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}
