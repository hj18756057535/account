package com.hypers.account.starter.web;

import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.contract.AccountIntegrationPaths;
import com.hypers.account.contract.application.AccountUserDesiredState;
import com.hypers.account.contract.application.AccountUserSyncResult;
import com.hypers.account.starter.properties.AccountIntegrationProperties;
import com.hypers.account.starter.spi.AccountUserSyncHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountUserSyncController {

    private final AccountIntegrationProperties properties;
    private final AccountUserSyncHandler handler;
    private final AccountUserSyncGuard guard;

    @PutMapping(AccountIntegrationPaths.APPLICATION_USER)
    public AccountUserSyncResult apply(
            @PathVariable String globalUserId,
            @RequestHeader(value = AccountIntegrationHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody AccountUserDesiredState desiredState) {
        validate(globalUserId, idempotencyKey, desiredState);
        return guard.apply(idempotencyKey, desiredState, handler);
    }

    private void validate(String globalUserId,
                          String idempotencyKey,
                          AccountUserDesiredState desiredState) {
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || desiredState == null
                || !properties.getAppCode().equals(desiredState.getAppCode())
                || !globalUserId.equals(desiredState.getGlobalUserId())
                || isBlank(desiredState.getAccount())
                || isBlank(desiredState.getDisplayName())
                || isBlank(desiredState.getTenantCode())
                || desiredState.getSyncVersion() < 1
                || desiredState.getOccurredAt() == null
                || !("enabled".equals(desiredState.getDesiredStatus())
                || "disabled".equals(desiredState.getDesiredStatus()))) {
            throw new AccountIntegrationException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "VALIDATION_FAILED",
                    "用户同步请求不符合契约");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
