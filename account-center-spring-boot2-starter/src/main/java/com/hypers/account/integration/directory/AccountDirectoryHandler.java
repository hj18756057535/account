package com.hypers.account.integration.directory;

/** 宿主按已配置租户读取一页来源目录。 */
public interface AccountDirectoryHandler {
    AccountDirectoryPage query(AccountDirectoryQuery query);
}

