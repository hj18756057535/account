package com.hypers.account.integration.user;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** 经应用 HMAC 校验的用户期望状态。 */
@Getter
@Setter
public class AccountUserDesiredState {
    /** 目标应用编码。 */
    private String appCode;
    /** Account 稳定全局身份 ID。 */
    private String globalUserId;
    /** 已确认的宿主用户 ID，可为空。 */
    private String localUserId;
    /** 统一账户名。 */
    private String account;
    /** 用户姓名。 */
    private String displayName;
    /** 邮箱。 */
    private String email;
    /** 手机号。 */
    private String phone;
    /** 应用目标租户。 */
    private String tenantCode;
    /** 期望准入状态：enabled 或 disabled。 */
    private String desiredStatus;
    /** 单调递增的准入版本。 */
    private long syncVersion;
    /** 事件产生时间。 */
    private Instant occurredAt;
}

