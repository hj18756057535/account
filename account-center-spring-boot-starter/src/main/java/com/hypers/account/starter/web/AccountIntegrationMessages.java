package com.hypers.account.starter.web;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.context.support.ResourceBundleMessageSource;

/** An isolated catalog so the Starter never replaces the host application's message source. */
public final class AccountIntegrationMessages {
    private static final ResourceBundleMessageSource SOURCE = source();

    private AccountIntegrationMessages() { }

    private static ResourceBundleMessageSource source() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("com/hypers/account/starter/i18n/messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);
        return source;
    }

    public static Locale locale(HttpServletRequest request) {
        String header = request.getHeader("Accept-Language");
        if (header != null && !header.isBlank()) {
            try {
                for (var range : Locale.LanguageRange.parse(header)) {
                    if (range.getWeight() == 0) continue;
                    String language = range.getRange().split("-", 2)[0];
                    if ("en".equals(language)) return Locale.US;
                    if ("zh".equals(language) || "*".equals(language)) return Locale.SIMPLIFIED_CHINESE;
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid headers do not change request authentication or validation.
            }
        }
        return Locale.SIMPLIFIED_CHINESE;
    }

    public static String text(HttpServletRequest request, String code) {
        Locale locale = locale(request);
        String fallback = SOURCE.getMessage("INTEGRATION_ERROR", null, locale);
        return code == null ? fallback : SOURCE.getMessage(code, null, fallback, locale);
    }
}
