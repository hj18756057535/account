package com.hypers.account.contract;

import lombok.experimental.UtilityClass;

/** 目录读取与用户同步的共享服务鉴权协议。 */
@UtilityClass
public class AccountProviderProtocol {
    public static final String VERSION = "account_provider";
    public static final String DIRECTORY_PATH = "/account-integration/directory/query";
    public static final String USERS_PATH = "/account-integration/users";
}

