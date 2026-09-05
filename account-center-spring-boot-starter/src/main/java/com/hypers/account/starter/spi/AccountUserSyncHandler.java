package com.hypers.account.starter.spi;

import com.hypers.account.contract.application.AccountUserDesiredState;
import com.hypers.account.contract.application.AccountUserSyncResult;

@FunctionalInterface
public interface AccountUserSyncHandler {

    AccountUserSyncResult apply(AccountUserDesiredState desiredState);

    /**
     * 托管同步的幂等上下文入口。新应用应覆盖本方法，在业务事务中保存键、请求摘要、版本及结果。
     * 默认委托旧入口，只保持已有 Lambda/Handler 兼容，不提供跨重启的幂等保证。
     */
    default AccountUserSyncResult apply(String idempotencyKey, AccountUserDesiredState desiredState) {
        return apply(desiredState);
    }
}
