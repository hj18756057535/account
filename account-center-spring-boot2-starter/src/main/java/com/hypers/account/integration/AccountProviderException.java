package com.hypers.account.integration;

import lombok.Getter;

/** 向调用方返回受控错误码，不暴露数据库或凭据。 */
@Getter
public class AccountProviderException extends RuntimeException {
    private final int status;
    private final String code;

    public AccountProviderException(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}

