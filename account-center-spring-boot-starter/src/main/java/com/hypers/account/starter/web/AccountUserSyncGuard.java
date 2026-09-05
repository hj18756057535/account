package com.hypers.account.starter.web;

import com.hypers.account.contract.application.AccountUserDesiredState;
import com.hypers.account.contract.application.AccountUserSyncResult;
import com.hypers.account.starter.spi.AccountUserSyncHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;

public class AccountUserSyncGuard {

    private static final int LOCK_STRIPES = 64;

    private final Object[] locks = IntStream.range(0, LOCK_STRIPES).mapToObj(ignored -> new Object()).toArray();
    private final Map<String, AppliedState> latestByUser = new ConcurrentHashMap<>();

    public AccountUserSyncResult apply(String idempotencyKey,
                                       AccountUserDesiredState desiredState,
                                       AccountUserSyncHandler handler) {
        String userKey = desiredState.getAppCode() + ':' + desiredState.getGlobalUserId();
        synchronized (locks[Math.floorMod(userKey.hashCode(), LOCK_STRIPES)]) {
            AppliedState previous = latestByUser.get(userKey);
            if (previous != null && previous.idempotencyKey().equals(idempotencyKey)) {
                return previous.result();
            }
            if (previous != null && desiredState.getSyncVersion() <= previous.result().getAppliedVersion()) {
                throw new AccountIntegrationException(
                        HttpStatus.CONFLICT,
                        "RESOURCE_VERSION_CONFLICT",
                        "同步版本不是最新版本");
            }
            AccountUserSyncResult result;
            try {
                result = handler.apply(idempotencyKey, desiredState);
            } catch (AccountIntegrationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new AccountIntegrationException(
                        HttpStatus.BAD_GATEWAY,
                        "DEPENDENCY_UNAVAILABLE",
                        "业务系统未能应用用户同步请求");
            }
            validateResult(desiredState, result);
            latestByUser.put(userKey, new AppliedState(idempotencyKey, result));
            return result;
        }
    }

    private void validateResult(AccountUserDesiredState desiredState, AccountUserSyncResult result) {
        if (result == null
                || !desiredState.getAppCode().equals(result.getAppCode())
                || !desiredState.getGlobalUserId().equals(result.getGlobalUserId())
                || result.getLocalUserId() == null
                || result.getLocalUserId().isBlank()
                || !desiredState.getDesiredStatus().equals(result.getAppliedStatus())
                || result.getAppliedVersion() < desiredState.getSyncVersion()
                || result.getResultCode() == null
                || result.getResultCode().isBlank()) {
            throw new AccountIntegrationException(
                    HttpStatus.BAD_GATEWAY,
                    "DEPENDENCY_UNAVAILABLE",
                    "业务系统返回的用户同步结果无效");
        }
    }

    private record AppliedState(String idempotencyKey, AccountUserSyncResult result) {
    }
}
