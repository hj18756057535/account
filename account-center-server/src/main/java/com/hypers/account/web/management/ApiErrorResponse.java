package com.hypers.account.web.management;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.Map;
import lombok.Getter;

@Getter
public class ApiErrorResponse {

    private final String code;
    private final String message;
    private final String traceId;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final Map<String, String> fieldErrors;

    public ApiErrorResponse(String code, String message, String traceId) {
        this(code, message, traceId, Collections.emptyMap());
    }

    public ApiErrorResponse(String code,
                            String message,
                            String traceId,
                            Map<String, String> fieldErrors) {
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.fieldErrors = fieldErrors == null ? Collections.emptyMap() : fieldErrors;
    }

}
