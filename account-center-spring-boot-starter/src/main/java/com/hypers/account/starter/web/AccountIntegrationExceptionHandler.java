package com.hypers.account.starter.web;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AccountUserSyncController.class, AccountMenuPermissionController.class})
public class AccountIntegrationExceptionHandler {

    @ExceptionHandler(AccountIntegrationException.class)
    public ResponseEntity<AccountIntegrationErrorResponse> handle(AccountIntegrationException exception,
                                                                   HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus())
                .header("Content-Language", AccountIntegrationMessages.locale(request).toLanguageTag())
                .header("Vary", "Accept-Language").body(AccountIntegrationErrorResponse.builder()
                .code(exception.getCode())
                .message(AccountIntegrationMessages.text(request, exception.getCode()))
                .traceId(UUID.randomUUID().toString())
                .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AccountIntegrationErrorResponse> handleMalformedRequest(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header("Content-Language", AccountIntegrationMessages.locale(request).toLanguageTag())
                .header("Vary", "Accept-Language").body(AccountIntegrationErrorResponse.builder()
                .code("MALFORMED_REQUEST")
                .message(AccountIntegrationMessages.text(request, "MALFORMED_REQUEST"))
                .traceId(UUID.randomUUID().toString())
                .build());
    }
}
