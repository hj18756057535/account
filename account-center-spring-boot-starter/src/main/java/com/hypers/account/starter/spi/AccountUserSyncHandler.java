package com.hypers.account.starter.spi;

import com.hypers.account.contract.application.AccountUserDesiredState;
import com.hypers.account.contract.application.AccountUserSyncResult;

@FunctionalInterface
public interface AccountUserSyncHandler {

    AccountUserSyncResult apply(AccountUserDesiredState desiredState);
}
