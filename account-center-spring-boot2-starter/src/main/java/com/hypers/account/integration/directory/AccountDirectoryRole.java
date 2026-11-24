package com.hypers.account.integration.directory;

import lombok.Getter;
import lombok.Setter;

/** 应用来源目录的受控传输对象，不包含密码与令牌。 */
@Getter
@Setter
public class AccountDirectoryRole {
    /** CMS 本地角色 ID，十进制字符串。 */
    private String localRoleId;

    /** CMS 角色编码。 */
    private String code;

    /** CMS 角色名称。 */
    private String name;

    /** 本地状态：enabled 或 disabled。 */
    private String status;
}

