package com.hypers.account.app.model;

import lombok.Getter;

/** 菜单权限协议调用失败，携带下游返回的状态码和错误编码。 */
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
