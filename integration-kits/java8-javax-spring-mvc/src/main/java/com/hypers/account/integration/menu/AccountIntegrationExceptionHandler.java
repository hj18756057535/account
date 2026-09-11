package com.hypers.account.integration.menu;

import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AccountMenuPermissionController.class)
public class AccountIntegrationExceptionHandler {

    @ExceptionHandler(AccountIntegrationException.class)
    public ResponseEntity<AccountIntegrationErrorResponse> handle(
            AccountIntegrationException exception,
            HttpServletRequest request) {
        return response(exception.getStatus(), exception.getCode(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AccountIntegrationErrorResponse> malformed(HttpServletRequest request) {
        return response(400, "MALFORMED_REQUEST", request);
    }

    private ResponseEntity<AccountIntegrationErrorResponse> response(
            int status,
            String code,
            HttpServletRequest request) {
        AccountIntegrationErrorResponse error = new AccountIntegrationErrorResponse();
        error.setCode(code);
        error.setMessage(AccountIntegrationMessages.text(request, code));
        error.setTraceId(UUID.randomUUID().toString());
        return ResponseEntity.status(status)
                .header("Content-Language", AccountIntegrationMessages.language(request))
                .header("Vary", "Accept-Language")
                .body(error);
    }
}
