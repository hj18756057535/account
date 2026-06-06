package com.hypers.account.starter.endpoint;

import com.hypers.account.starter.dto.AccountUserUpsertRequest;

/**
 * 应用侧用户同步接口。
 * 业务应用实现此接口，处理 Account Center 推送的用户创建/更新和禁用事件。
 *
 * 授权用户时 Account Center 调用 /account-sso/internal/users/upsert（enabled=true）
 * 取消授权或禁用用户时调用 /account-sso/internal/users/upsert（enabled=false）
 */
public interface AccountUserProvisionService {

    /**
     * 创建或更新本地用户。
     * 如果用户已存在，按 account 定位并更新信息；不存在则创建。
     *
     * @param request 用户同步请求（含 appCode, tenantCode, externalUserId, account, email, name, phone, enabled）
     */
    void upsertUser(AccountUserUpsertRequest request);

    /**
     * 禁用本地用户。
     * 当 enabled=false 时由默认 Controller 调用此方法。
     *
     * @param request 用户同步请求
     */
    void disableUser(AccountUserUpsertRequest request);
}
