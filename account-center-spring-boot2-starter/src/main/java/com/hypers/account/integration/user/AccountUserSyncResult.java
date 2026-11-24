package com.hypers.account.integration.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

/** 已持久化的用户同步回执。 */
@Getter
@Setter
public class AccountUserSyncResult {
    /** 目标应用编码。 */
    private String appCode;
    /** Account 全局身份 ID。 */
    private String globalUserId;
    /** 宿主用户 ID，始终使用字符串。 */
    private String localUserId;
    /** 已应用的准入状态。 */
    private String appliedStatus;
    /** 协议要求版本为 JSON 整数，避免宿主全局 Long 字符串策略改变契约。 */
    @JsonSerialize(using = AccountSyncVersionSerializer.class)
    private long appliedVersion;
    /** 结果：APPLIED 或 ALREADY_APPLIED。 */
    private String resultCode;
}

