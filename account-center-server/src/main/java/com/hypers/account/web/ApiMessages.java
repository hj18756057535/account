package com.hypers.account.web;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/** Resolves API message keys at the response boundary without changing business data. */
@Component
public class ApiMessages {

    private final ResourceBundleMessageSource source = createSource();

    private static ResourceBundleMessageSource createSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/api");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        return source;
    }

    public Locale locale(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header != null && !header.isBlank()) {
            try {
                for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
                    if (range.getWeight() == 0) continue;
                    String language = range.getRange().split("-", 2)[0];
                    if ("en".equals(language)) return Locale.US;
                    if ("zh".equals(language) || "*".equals(language)) return Locale.SIMPLIFIED_CHINESE;
                }
            } catch (IllegalArgumentException ignored) {
                // Malformed language headers must not fail otherwise valid requests.
            }
        }
        return Locale.SIMPLIFIED_CHINESE;
    }

    public String text(HttpServletRequest request, String key) {
        Locale locale = locale(request);
        String fallback = source.getMessage("error.invalidRequest", null, locale);
        return key == null ? fallback : source.getMessage(key, null, fallback, locale);
    }

    public Map<String, String> fields(HttpServletRequest request, Map<String, String> keys) {
        Map<String, String> translated = new LinkedHashMap<>();
        keys.forEach((field, key) -> translated.put(field, text(request, key)));
        return translated;
    }
}
