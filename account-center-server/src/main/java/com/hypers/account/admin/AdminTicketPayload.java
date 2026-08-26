package com.hypers.account.admin;

/**
 * Admin ticket 验证结果 DTO。
 * 验证成功后返回关联的应用编码和用户 ID。
 */
public class AdminTicketPayload {

    private final String appCode;
    private final String userId;

    public AdminTicketPayload(String appCode, String userId) {
        this.appCode = appCode;
        this.userId = userId;
    }

    public String getAppCode() {
        return appCode;
    }

    public String getUserId() {
        return userId;
    }
}
