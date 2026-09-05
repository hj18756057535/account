package com.hypers.account.app;

import com.hypers.account.contract.application.AccountUserSyncResult;

public interface ApplicationSyncTransport {
    AccountUserSyncResult send(ApplicationSyncDelivery delivery, AccountApplication application);
}
