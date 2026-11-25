package com.hypers.account.app.service;

import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.model.ApplicationSyncFailure;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.config.ApplicationSyncProperties;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationSyncPolicy {
    private final ApplicationSyncProperties properties;

    public boolean enabled() {
        return properties.isEnabled();
    }

    public URI target(AccountApplication application) {
        if (!enabled()) {
            throw new ApplicationSyncFailure(ErrorCode.SYNC_DISABLED, false);
        }
        if (application == null) {
            throw new ApplicationSyncFailure(ErrorCode.SYNC_TARGET_UNAVAILABLE, false);
        }
        URI target;
        try {
            target = URI.create(application.getNotifyBaseUrl());
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new ApplicationSyncFailure(ErrorCode.SYNC_TARGET_UNAVAILABLE, false);
        }
        if (!"enabled".equals(application.getStatus())
                || !"active".equals(application.getSecretState()) || application.getSecret() == null
                || application.getSecret().isBlank() || !application.hasProviderConfiguration()) {
            throw new ApplicationSyncFailure(ErrorCode.SYNC_TARGET_UNAVAILABLE, false);
        }
        boolean loopback = "http".equals(target.getScheme())
                && ("localhost".equalsIgnoreCase(target.getHost()) || "127.0.0.1".equals(target.getHost())
                || "[::1]".equals(target.getHost()));
        if ((!loopback && !"https".equals(target.getScheme())) || target.getHost() == null
                || target.getUserInfo() != null || target.getRawQuery() != null || target.getRawFragment() != null
                || !target.normalize().equals(target) || target.getRawPath().contains("%")
                || !trim(target.toString()).equals(trim(application.getNotifyBaseUrl()))) {
            throw new ApplicationSyncFailure(ErrorCode.SYNC_TARGET_UNAVAILABLE, false);
        }
        return URI.create(trim(target.toString()));
    }

    public boolean canDeliver(AccountApplication app) {
        try {
            target(app);
            return true;
        } catch (ApplicationSyncFailure | IllegalArgumentException error) {
            return false;
        }
    }

    private String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
