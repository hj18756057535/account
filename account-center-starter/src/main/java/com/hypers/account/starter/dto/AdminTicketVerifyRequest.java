package com.hypers.account.starter.dto;

/**
 * 管理 ticket 校验请求。
 * 应用 iframe 后端调用 Account Center 校验管理 ticket。
 */
public class AdminTicketVerifyRequest {

    private String appCode;
    private String ticket;

    public AdminTicketVerifyRequest() {
    }

    public AdminTicketVerifyRequest(String appCode, String ticket) {
        this.appCode = appCode;
        this.ticket = ticket;
    }

    public String getAppCode() { return appCode; }
    public void setAppCode(String appCode) { this.appCode = appCode; }
    public String getTicket() { return ticket; }
    public void setTicket(String ticket) { this.ticket = ticket; }
}
