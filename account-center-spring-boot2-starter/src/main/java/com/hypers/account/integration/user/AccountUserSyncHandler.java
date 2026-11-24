package com.hypers.account.integration.user;

/** 宿主在同一事务中维护关联及当前准入版本/摘要，按用户版本幂等；请求键用于跟踪。 */
public interface AccountUserSyncHandler {
    AccountUserSyncResult apply(String idempotencyKey, AccountUserDesiredState command);
}

