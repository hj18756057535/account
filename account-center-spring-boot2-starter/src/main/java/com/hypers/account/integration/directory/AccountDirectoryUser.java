package com.hypers.account.integration.directory;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 应用来源目录的受控传输对象，不包含密码与令牌。 */
@Getter
@Setter
public class AccountDirectoryUser {
    /** CMS 本地用户 ID，十进制字符串。 */
    private String localUserId;

    /** 用于跨系统首次关联的统一账户名。 */
    private String account;

    /** 用户姓名。 */
    private String name;

    /** 邮箱，可为空。 */
    private String email;

    /** 手机号，可为空。 */
    private String phone;

    /** 本地状态：enabled 或 disabled。 */
    private String status;

    /** 本地角色 ID 列表。 */
    private List<String> roleIds = new ArrayList<>();

    /** 用户所属主组织的本地 ID 列表。 */
    private List<String> organizationIds = new ArrayList<>();
}

