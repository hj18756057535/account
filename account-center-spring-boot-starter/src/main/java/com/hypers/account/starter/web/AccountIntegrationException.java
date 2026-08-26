package com.hypers.account.starter.web;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class AccountIntegrationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AccountIntegrationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
