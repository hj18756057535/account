package com.hypers.account.integration.menu;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountMenuPermissionController {

    private final String appCode;
    private final AccountMenuPermissionHandler handler;
    private final MenuPermissionContractValidator validator = new MenuPermissionContractValidator();

    @PostMapping(AccountMenuPermissionProtocol.QUERY_PATH)
    public MenuPermissionSnapshot query(
            @RequestHeader(value = AccountMenuPermissionProtocol.PROTOCOL_VERSION_HEADER, required = false)
            String protocolVersion,
            @RequestBody MenuPermissionQuery query) {
        validator.validateQuery(appCode, protocolVersion, query);
        try {
            return validator.normalizeSnapshot(handler.query(query));
        } catch (AccountIntegrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AccountIntegrationException(503, "PERMISSION_PROVIDER_UNAVAILABLE");
        }
    }

    @PutMapping(AccountMenuPermissionProtocol.REPLACE_PATH)
    public MenuPermissionSnapshot replace(
            @RequestHeader(value = AccountMenuPermissionProtocol.PROTOCOL_VERSION_HEADER, required = false)
            String protocolVersion,
            @RequestHeader(value = AccountMenuPermissionProtocol.IDEMPOTENCY_KEY_HEADER, required = false)
            String idempotencyKey,
            @RequestBody MenuPermissionReplaceCommand command) {
        validator.validateReplace(appCode, protocolVersion, idempotencyKey, command);
        try {
            return validator.normalizeSnapshot(handler.replace(idempotencyKey, command));
        } catch (AccountIntegrationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AccountIntegrationException(503, "PERMISSION_PROVIDER_UNAVAILABLE");
        }
    }
}
