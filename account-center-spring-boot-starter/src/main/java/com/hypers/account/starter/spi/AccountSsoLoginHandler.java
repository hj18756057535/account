package com.hypers.account.starter.spi;

import com.hypers.account.contract.account.SsoTicketExchangeResponse;

@FunctionalInterface
public interface AccountSsoLoginHandler {

    String handleLogin(SsoTicketExchangeResponse user);
}
