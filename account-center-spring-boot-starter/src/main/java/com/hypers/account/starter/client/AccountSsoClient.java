package com.hypers.account.starter.client;

import com.hypers.account.contract.AccountIntegrationPaths;
import com.hypers.account.contract.account.SsoTicketExchangeRequest;
import com.hypers.account.contract.account.SsoTicketExchangeResponse;
import lombok.RequiredArgsConstructor;

/**
 * Account Center SSO 客户端。
 * 应用后端收到 SSO callback 后，使用此客户端向 Account Center 兑换用户信息。
 */
@RequiredArgsConstructor
public class AccountSsoClient {

    private final AccountProviderClient providerClient;

    /** 用 SSO code 向 Account Center 兑换用户信息 */
    public SsoTicketExchangeResponse exchange(String code) {
        return providerClient.post(
                AccountIntegrationPaths.SSO_TICKET_EXCHANGE,
                new SsoTicketExchangeRequest(providerClient.getAppCode(), code),
                SsoTicketExchangeResponse.class);
    }
}
