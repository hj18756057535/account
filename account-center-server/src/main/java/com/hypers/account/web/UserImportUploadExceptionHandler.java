package com.hypers.account.web;

import com.hypers.account.web.management.ApiErrorResponse;
import com.hypers.account.web.management.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class UserImportUploadExceptionHandler {
    private final ApiMessages messages;

    // MultipartResolver 可能在确定 Controller 之前失败，不能仅依赖管理包 Advice。
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorResponse> handle(MultipartException exception, HttpServletRequest request) {
        boolean exceeded = exception instanceof MaxUploadSizeExceededException;
        return ResponseEntity.status(exceeded ? 413 : 422).header("Cache-Control", "no-store")
                .body(new ApiErrorResponse(exceeded ? "IMPORT_LIMIT_EXCEEDED" : "IMPORT_FILE_INVALID",
                        messages.text(request, exceeded ? "import.tooLarge" : "import.invalidFile"),
                        RequestTraceFilter.traceId(request)));
    }
}
