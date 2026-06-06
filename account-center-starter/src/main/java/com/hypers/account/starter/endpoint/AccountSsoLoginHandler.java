package com.hypers.account.starter.endpoint;

import com.hypers.account.starter.dto.SsoTicketExchangeResponse;

/**
 * 应用侧 SSO 登录处理器。
 * 业务应用实现此接口，处理 SSO code 兑换成功后的本地登录逻辑。
 */
public interface AccountSsoLoginHandler {

    /**
     * SSO code 兑换成功后调用。
     * 应用根据 account 匹配或创建本地用户，签发自己的本地 token。
     *
     * @param user 用户快照（来自 Account Center）
     * @return 本地 token 或重定向 URL，由应用决定
     */
    String handleLogin(SsoTicketExchangeResponse user);
}
