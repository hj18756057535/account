package com.hypers.account.app.directory;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 管理员目录预览和已提交结果的持久化记录。 */
@Getter
@Setter
public class DirectoryImportRecord {
    /** 预览记录 UUID */
    private String id;

    /** 来源应用 */
    private String appCode;

    /** 预览发起人 */
    private String operatorId;

    /** 预览时应用版本 */
    private long applicationVersion;

    /** preview 或 committed */
    private String status;

    /** 不含凭据的来源快照 */
    private String snapshotJson;

    /** 提交结果 */
    private String resultJson;

    /** 创建时间 */
    private Instant createdAt;

    private String createdBy;
    private String updatedBy;
    private Instant updatedAt;

    /** 预览失效时间 */
    private Instant expiresAt;

    /** 提交完成时间 */
    private Instant committedAt;
}

