package com.hypers.account.app.service;

import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.model.ApplicationSyncDelivery;
import com.hypers.account.contract.application.AccountUserSyncResult;

/** 用户状态下发的传输抽象，生产实现走 HTTP。 */
public interface ApplicationSyncTransport {
    AccountUserSyncResult send(ApplicationSyncDelivery delivery, AccountApplication application);
}
