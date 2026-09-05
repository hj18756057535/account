package com.hypers.account.app;

import com.hypers.account.config.ApplicationSyncProperties;
import java.net.URI;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationSyncPolicy {
    private final ApplicationSyncProperties properties;
    private final Environment environment;

    public boolean databaseMode() {
        return "mybatis".equals(environment.getProperty("account.store.type", "mybatis"));
    }

    public boolean enabled() { return databaseMode() && properties.isEnabled(); }

    public URI target(AccountApplication application) {
        if (!enabled()) throw new ApplicationSyncFailure("SYNC_DISABLED", false);
        if (application == null) throw new ApplicationSyncFailure("SYNC_TARGET_UNAVAILABLE", false);
        URI target = properties.getTargets().get(application.getAppCode());
        if (target == null || !"enabled".equals(application.getStatus())
                || !"active".equals(application.getSecretState()) || application.getSecret() == null
                || application.getSecret().isBlank() || application.getProtocolCapabilities() == null
                || application.getNotifyBaseUrl() == null
                || !Arrays.asList(application.getProtocolCapabilities().split(",")).contains("user_sync")) {
            throw new ApplicationSyncFailure("SYNC_TARGET_UNAVAILABLE", false);
        }
        boolean loopbackTest = environment.acceptsProfiles(Profiles.of("test"))
                && "http".equals(target.getScheme())
                && ("127.0.0.1".equals(target.getHost()) || "[::1]".equals(target.getHost()));
        if ((!loopbackTest && !"https".equals(target.getScheme())) || target.getHost() == null
                || target.getUserInfo() != null || target.getRawQuery() != null || target.getRawFragment() != null
                || !target.normalize().equals(target) || target.getRawPath().contains("%")
                || !trim(target.toString()).equals(trim(application.getNotifyBaseUrl()))) {
            throw new ApplicationSyncFailure("SYNC_TARGET_UNAVAILABLE", false);
        }
        return URI.create(trim(target.toString()));
    }

    public boolean canDeliver(AccountApplication app) {
        try { target(app); return true; }
        catch (ApplicationSyncFailure | IllegalArgumentException error) { return false; }
    }

    private String trim(String url) { return url.endsWith("/") ? url.substring(0, url.length() - 1) : url; }
}
