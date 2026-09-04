package com.hypers.account.web;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/** Localizes container fallback errors without exposing exception messages or stack traces. */
@Component
@RequiredArgsConstructor
public class LocalizedErrorAttributes extends DefaultErrorAttributes {
    private final ApiMessages messages;

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        var attributes = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
        if (request instanceof ServletWebRequest servletRequest) {
            int status = ((Number) attributes.getOrDefault("status", 500)).intValue();
            attributes.put("error", messages.text(servletRequest.getRequest(), messages.httpErrorKey(status)));
        }
        return attributes;
    }
}
