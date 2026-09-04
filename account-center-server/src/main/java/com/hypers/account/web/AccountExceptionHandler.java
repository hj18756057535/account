package com.hypers.account.web;

import com.hypers.account.app.http.ApplicationSyncException;
import com.hypers.account.sso.SsoTicketException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理。
 * 统一将业务异常转换为结构化 JSON 响应。
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class AccountExceptionHandler {

    private final ApiMessages messages;

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                              HttpServletRequest request) {
        String key = switch (String.valueOf(ex.getMessage())) {
            case "user not found" -> "error.userNotFound";
            case "application not found" -> "error.applicationNotFound";
            case "application access not found" -> "error.resourceNotFound";
            case "application is disabled" -> "error.applicationDisabled";
            case "user is disabled", "user is not authorized for application" -> "error.accessDenied";
            case "ticket 无效或已过期", "ticket 已被使用" -> "error.ticket";
            default -> "error.invalidRequest";
        };
        return ResponseEntity.badRequest().body(new ErrorResponse(messages.text(request, key)));
    }

    @ExceptionHandler(SsoTicketException.class)
    public ResponseEntity<ErrorResponse> handleSsoTicket(SsoTicketException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(messages.text(request, "error.ticket")));
    }

    /** 应用同步失败返回 502，表示上游应用不可用 */
    @ExceptionHandler(ApplicationSyncException.class)
    public ResponseEntity<ErrorResponse> handleSyncException(ApplicationSyncException ex, HttpServletRequest request) {
        return ResponseEntity.status(502).body(new ErrorResponse(messages.text(request, "error.sync")));
    }

    @Value
    public static class ErrorResponse {
        String error;
    }
}
