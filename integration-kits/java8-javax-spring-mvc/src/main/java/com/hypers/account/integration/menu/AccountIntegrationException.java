package com.hypers.account.integration.menu;

import lombok.Getter;

@Getter
public class AccountIntegrationException extends RuntimeException {

    private final int status;
    private final String code;

    public AccountIntegrationException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}
