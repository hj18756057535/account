package com.hypers.account.app.directory;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 提交时重新校验应用版本与身份冲突。 */
@Getter
@Setter
public class DirectoryImportPreview {
    /** 持久预览 UUID */
    private String importId;

    /** 当前预览状态 */
    private String status;

    /** 预览失效时间 */
    private Instant expiresAt;

    /** 预计新增全局用户数 */
    private int newUsers;

    /** 预计复用全局用户数 */
    private int existingUsers;

    /** 用户角色组织来源快照 */
    private ApplicationDirectorySnapshot snapshot;
}

