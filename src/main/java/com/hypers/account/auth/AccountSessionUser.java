package com.hypers.account.auth;

import java.io.Serializable;

/**
 * Account Center 登录会话用户。
 * 存储在 HttpSession 中，标识当前已登录的管理员。
 */
public class AccountSessionUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String userId;
    private final String account;
    private final String name;

    public AccountSessionUser(String userId, String account, String name) {
        this.userId = userId;
        this.account = account;
        this.name = name;
    }

    public String getUserId() {
        return userId;
    }

    public String getAccount() {
        return account;
    }

    public String getName() {
        return name;
    }
}
