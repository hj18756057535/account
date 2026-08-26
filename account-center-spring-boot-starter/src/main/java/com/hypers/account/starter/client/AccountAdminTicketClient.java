package com.hypers.account.starter.client;

import com.hypers.account.contract.AccountIntegrationPaths;
import com.hypers.account.contract.account.AdminTicketVerifyRequest;
import com.hypers.account.contract.account.AdminTicketVerifyResponse;
import lombok.RequiredArgsConstructor;

/**
 * Account Center 管理 ticket 客户端。
 * 应用 iframe 后端使用此客户端向 Account Center 校验管理 ticket。
 */
@RequiredArgsConstructor
public class AccountAdminTicketClient {

    private final AccountProviderClient providerClient;

    /** 校验管理 ticket，返回 appCode 和 userId */
    public AdminTicketVerifyResponse verify(String ticket) {
        return providerClient.post(
                AccountIntegrationPaths.ADMIN_TICKET_VERIFY,
                new AdminTicketVerifyRequest(providerClient.getAppCode(), ticket),
                AdminTicketVerifyResponse.class);
    }
}
