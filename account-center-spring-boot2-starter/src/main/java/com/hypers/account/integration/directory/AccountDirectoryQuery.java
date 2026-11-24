package com.hypers.account.integration.directory;

import lombok.Getter;
import lombok.Setter;

/** 应用来源目录的受控传输对象，不包含密码与令牌。 */
@Getter
@Setter
public class AccountDirectoryQuery {
    /** 目录种类：users、roles 或 organizations。 */
    private String kind = "users";

    /** 上一页最后一条本地 ID，首请求为空。 */
    private String afterId;

    /** 每页数量，允许 1 至 200。 */
    private int limit = 200;
}

