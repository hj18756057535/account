package com.hypers.account.starter.web;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AccountIntegrationErrorResponse {

    private final String code;
    private final String message;
    private final String traceId;
    @Builder.Default
    private final List<String> fieldErrors = List.of();
}
