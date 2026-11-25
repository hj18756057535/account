package com.hypers.account.web;

import com.hypers.account.app.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 全局异常处理。
 * 统一将业务异常转换为结构化 JSON 响应。
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class AccountExceptionHandler {

    private final ApiMessages messages;

    @ExceptionHandler({ServletRequestBindingException.class, HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class,
            NoResourceFoundException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, MethodArgumentNotValidException.class, ResponseStatusException.class})
    public ResponseEntity<AccountExceptionHandler.ErrorResponse> handleHttpFailure(Exception exception,
                                                                                 HttpServletRequest request) {
        int status = exception instanceof org.springframework.web.ErrorResponse error ? error.getStatusCode().value() : 400;
        var response = ResponseEntity.status(status);
        if (exception instanceof org.springframework.web.ErrorResponse error) response.headers(error.getHeaders());
        return response.body(new AccountExceptionHandler.ErrorResponse(messages.text(request, messages.httpErrorKey(status))));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ErrorResponse(messages.text(request, "error.invalidRequest")));
    }

    /** 业务异常自带状态和国际化消息键，不在这里拼接文案。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(messages.text(request, ex.getMessageKey())));
    }

    @Value
    public static class ErrorResponse {
        String error;
    }
}
