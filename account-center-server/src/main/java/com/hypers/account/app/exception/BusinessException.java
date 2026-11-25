package com.hypers.account.app.exception;

import java.util.Collections;
import java.util.Map;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>异常只携带 {@link ErrorCode} 和可选的字段级消息键；HTTP 状态、对外错误码和提示文案
 * 都由错误码枚举定义，web 层按请求语言解析，业务代码不拼接展示文本。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, Collections.emptyMap());
    }

    public BusinessException(ErrorCode errorCode, Map<String, String> fieldErrors) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null ? Collections.emptyMap() : fieldErrors;
    }

    public int getStatus() {
        return errorCode.getStatus();
    }

    public String getCode() {
        return errorCode.getCode();
    }

    public String getMessageKey() {
        return errorCode.getMessageKey();
    }
}
