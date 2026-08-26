package com.hypers.account.app;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class ApplicationUrlValidator {

    private static final List<String> HTTP_SCHEMES = Arrays.asList("http", "https");

    public void validate(RegisterApplicationCommand command) {
        validateHttpUrl("entryUrl", command.getEntryUrl());
        validateHttpUrl("ssoCallbackUrl", command.getSsoCallbackUrl());
        validateHttpUrl("permissionIframeUrl",
                command.getPermissionIframeUrl().replace("{externalUserId}", "externalUserId"));
        validateHttpUrl("notifyBaseUrl", command.getNotifyBaseUrl());
    }

    private void validateHttpUrl(String field, String value) {
        URI uri = parse(field, value);
        String scheme = uri.getScheme();
        if (scheme == null || !HTTP_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException(field + " must use http or https");
        }
        if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException(field + " host is required");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException(field + " must not contain user info");
        }
    }

    private URI parse(String field, String value) {
        try {
            return new URI(value);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}
