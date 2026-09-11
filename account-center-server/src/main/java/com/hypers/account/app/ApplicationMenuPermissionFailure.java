package com.hypers.account.app;

import lombok.Getter;

@Getter
public class ApplicationMenuPermissionFailure extends RuntimeException {

    private final int status;
    private final String code;

    public ApplicationMenuPermissionFailure(int status, String code) {
        super(code);
        this.status = status;
        this.code = code;
    }
}
