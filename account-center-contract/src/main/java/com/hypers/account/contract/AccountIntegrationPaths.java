package com.hypers.account.contract;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AccountIntegrationPaths {

    public static final String SSO_TICKET_EXCHANGE = "/openapi/sso/tickets/exchange";
    public static final String ADMIN_TICKET_VERIFY = "/openapi/admin-tickets/verify";
    public static final String APPLICATION_USER = "/account-integration/users/{globalUserId}";

    public static String applicationUser(String globalUserId) {
        return "/account-integration/users/" + globalUserId;
    }
}
