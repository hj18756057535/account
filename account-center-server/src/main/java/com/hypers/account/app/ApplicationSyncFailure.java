package com.hypers.account.app;

import lombok.Getter;

@Getter
public class ApplicationSyncFailure extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public ApplicationSyncFailure(String code, boolean retryable) {
        super(code);
        this.code = code;
        this.retryable = retryable;
    }
}
