package com.hypers.account.web;

import com.hypers.account.app.http.ApplicationSyncException;
import com.hypers.account.sso.SsoTicketException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 * 统一将业务异常转换为结构化 JSON 响应。
 */
@RestControllerAdvice
public class AccountExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(SsoTicketException.class)
    public ResponseEntity<ErrorResponse> handleSsoTicket(SsoTicketException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    /** 应用同步失败返回 502，表示上游应用不可用 */
    @ExceptionHandler(ApplicationSyncException.class)
    public ResponseEntity<ErrorResponse> handleSyncException(ApplicationSyncException ex) {
        return ResponseEntity.status(502).body(new ErrorResponse(ex.getMessage()));
    }

    public static class ErrorResponse {

        private final String error;

        public ErrorResponse(String error) {
            this.error = error;
        }

        public String getError() {
            return error;
        }
    }
}
