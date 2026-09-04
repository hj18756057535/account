package com.hypers.account.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Keeps the legacy error shape while avoiding container-generated, untranslated errors. */
@Component
@RequiredArgsConstructor
public class LegacyApiErrorWriter {
    private final ObjectMapper objectMapper;
    private final ApiMessages messages;

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String key)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), new AccountExceptionHandler.ErrorResponse(messages.text(request, key)));
    }
}
