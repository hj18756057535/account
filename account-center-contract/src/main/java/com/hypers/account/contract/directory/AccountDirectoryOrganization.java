package com.hypers.account.contract.directory;

import lombok.Getter;
import lombok.Setter;

/** 应用来源目录的受控传输对象，不包含密码与令牌。 */
@Getter
@Setter
public class AccountDirectoryOrganization {
    /** CMS 本地组织 ID，十进制字符串。 */
    private String localOrganizationId;

    /** 父组织 ID；根组织为空。 */
    private String parentId;

    /** CMS 组织编码。 */
    private String code;

    /** CMS 组织名称。 */
    private String name;

    /** 本地状态：enabled 或 disabled。 */
    private String status;
}

