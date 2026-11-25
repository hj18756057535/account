package com.hypers.account.app.model;

import com.hypers.account.app.exception.ErrorCode;
import lombok.Getter;

/** 同步投递失败，携带错误码与是否允许重试。 */
@Getter
public class ApplicationSyncFailure extends RuntimeException {
    private final ErrorCode errorCode;
    private final boolean retryable;

    public ApplicationSyncFailure(ErrorCode errorCode, boolean retryable) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String getCode() {
        return errorCode.getCode();
    }
}