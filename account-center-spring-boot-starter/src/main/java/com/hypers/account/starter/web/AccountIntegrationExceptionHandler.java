package com.hypers.account.starter.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AccountUserSyncController.class)
public class AccountIntegrationExceptionHandler {

    @ExceptionHandler(AccountIntegrationException.class)
    public ResponseEntity<AccountIntegrationErrorResponse> handle(AccountIntegrationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(AccountIntegrationErrorResponse.builder()
                .code(exception.getCode())
                .message(exception.getMessage())
                .traceId(UUID.randomUUID().toString())
                .build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AccountIntegrationErrorResponse> handleMalformedRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(AccountIntegrationErrorResponse.builder()
                .code("MALFORMED_REQUEST")
                .message("请求体无法解析")
                .traceId(UUID.randomUUID().toString())
                .build());
    }
}
