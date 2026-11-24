package com.hypers.account.app.directory;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 来源本地 ID 与全局用户的稳定关联，独立于准入和同步确认。 */
@Getter
@Setter
public class ApplicationUserLink {
    /** 应用编码 */
    private String appCode;

    /** Account 全局用户 ID */
    private String userId;

    /** 来源租户 */
    private String tenantCode;

    /** 来源本地用户 ID */
    private String localUserId;

    /** 确认关联的管理员 */
    private String createdBy;

    /** 关联时间 */
    private Instant createdAt;

    private String updatedBy;
    private Instant updatedAt;
}

