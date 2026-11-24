package com.hypers.account.app.directory;

import lombok.Getter;
import lombok.Setter;

/** 不包含凭据的导入回执。 */
@Getter
@Setter
public class DirectoryImportResult {
    /** 已完成的导入记录 */
    private String importId;

    /** 来源应用 */
    private String appCode;

    /** 新建全局用户数 */
    private int createdUsers;

    /** 复用全局用户数 */
    private int reusedUsers;

    /** 确认身份关联数 */
    private int linkedUsers;

    /** 保存来源角色数 */
    private int roles;

    /** 保存来源组织数 */
    private int organizations;
}

