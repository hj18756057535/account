package com.hypers.account.starter.client;

import lombok.Getter;

@Getter
public class AccountClientException extends RuntimeException {

    private final int statusCode;
    private final String code;

    public AccountClientException(String message, int statusCode, String code) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }

    public AccountClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.code = "DEPENDENCY_UNAVAILABLE";
    }
}
