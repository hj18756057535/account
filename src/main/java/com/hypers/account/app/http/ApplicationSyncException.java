package com.hypers.account.app.http;

/**
 * 应用同步失败异常。
 * 当应用返回非 2xx 状态码或网络超时时抛出。
 */
public class ApplicationSyncException extends RuntimeException {

    private final int statusCode;

    public ApplicationSyncException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public ApplicationSyncException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ApplicationSyncException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
