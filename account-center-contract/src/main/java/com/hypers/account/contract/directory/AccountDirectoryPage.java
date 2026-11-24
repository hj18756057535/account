package com.hypers.account.contract.directory;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 应用来源目录的受控传输对象，不包含密码与令牌。 */
@Getter
@Setter
public class AccountDirectoryPage {
    /** 提供目录的应用编码。 */
    private String appCode;
    /** 提供目录的固定租户，单租户使用 default。 */
    private String tenantCode;

    /** 本次返回的目录种类。 */
    private String kind;

    /** 下一页游标，空值表示结束。 */
    private String nextCursor;

    /** 用户目录。 */
    private List<AccountDirectoryUser> users = new ArrayList<>();

    /** 角色目录。 */
    private List<AccountDirectoryRole> roles = new ArrayList<>();

    /** 组织目录。 */
    private List<AccountDirectoryOrganization> organizations = new ArrayList<>();
}

