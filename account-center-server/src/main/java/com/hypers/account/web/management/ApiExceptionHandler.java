package com.hypers.account.web.management;

import com.hypers.account.web.ApiMessages;
import lombok.RequiredArgsConstructor;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.hypers.account.web.management")
@RequiredArgsConstructor
public class ApiExceptionHandler {

    private final ApiMessages messages;

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception,
                                                                HttpServletRequest request) {
        return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(
                exception.getCode(),
                messages.text(request, exception.getMessage()),
                RequestTraceFilter.traceId(request),
                messages.fields(request, exception.getFieldErrors())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception,
                                                              HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), messages.text(request, error.getDefaultMessage()));
        }
        return ResponseEntity.unprocessableEntity().body(new ApiErrorResponse(
                "VALIDATION_FAILED",
                messages.text(request, "error.validation"),
                RequestTraceFilter.traceId(request),
                fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                                  HttpServletRequest request) {
        return ResponseEntity.unprocessableEntity().body(new ApiErrorResponse(
                "VALIDATION_FAILED",
                messages.text(request, "error.unreadable"),
                RequestTraceFilter.traceId(request)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception,
                                                              HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                "INTERNAL_ERROR",
                messages.text(request, "error.internal"),
                RequestTraceFilter.traceId(request)));
    }
}
