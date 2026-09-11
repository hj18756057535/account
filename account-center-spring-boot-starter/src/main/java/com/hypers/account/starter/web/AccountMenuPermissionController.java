package com.hypers.account.starter.web;

import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.contract.AccountIntegrationPaths;
import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import com.hypers.account.starter.properties.AccountIntegrationProperties;
import com.hypers.account.starter.spi.AccountMenuPermissionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountMenuPermissionController {

    private final AccountIntegrationProperties properties;
    private final AccountMenuPermissionHandler handler;
    private final MenuPermissionContractValidator validator = new MenuPermissionContractValidator();

    @PostMapping(AccountIntegrationPaths.MENU_PERMISSION_QUERY)
    public MenuPermissionSnapshot query(
            @RequestHeader(value = AccountIntegrationHeaders.PROTOCOL_VERSION, required = false)
            String protocolVersion,
            @RequestBody MenuPermissionQuery query) {
        validator.validateQuery(properties.getAppCode(), protocolVersion, query);
        try {
            return validator.normalizeSnapshot(handler.query(query));
        } catch (AccountIntegrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @PutMapping(AccountIntegrationPaths.MENU_PERMISSION_REPLACE)
    public MenuPermissionSnapshot replace(
            @RequestHeader(value = AccountIntegrationHeaders.PROTOCOL_VERSION, required = false)
            String protocolVersion,
            @RequestHeader(value = AccountIntegrationHeaders.IDEMPOTENCY_KEY, required = false)
            String idempotencyKey,
            @RequestBody MenuPermissionReplaceCommand command) {
        validator.validateReplace(properties.getAppCode(), protocolVersion, idempotencyKey, command);
        try {
            return validator.normalizeSnapshot(handler.replace(idempotencyKey, command));
        } catch (AccountIntegrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private AccountIntegrationException unavailable() {
        return new AccountIntegrationException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PERMISSION_PROVIDER_UNAVAILABLE",
                "PERMISSION_PROVIDER_UNAVAILABLE");
    }
}
