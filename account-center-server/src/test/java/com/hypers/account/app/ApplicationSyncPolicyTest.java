package com.hypers.account.app;

import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.model.ApplicationSyncFailure;
import com.hypers.account.app.service.ApplicationSyncPolicy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hypers.account.config.ApplicationSyncProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;

class ApplicationSyncPolicyTest {
    @Test
    void registeredTargetIsAuthoritativeAndLoopbackWorksOutsideTheTestProfile() {
        var properties = new ApplicationSyncProperties();

        var policy = new ApplicationSyncPolicy(properties);
        var app = new AccountApplication("cms-ai", "CMS", "http://localhost:8850",
                "http://localhost:8850/callback", "http://localhost:8850", "http://localhost:9101",
                "synthetic-secret", "default");
        assertThat(policy.target(app)).isEqualTo(URI.create("http://localhost:9101"));
        app.setNotifyBaseUrl("http://public.example.test");
        assertThatThrownBy(() -> policy.target(app)).isInstanceOf(ApplicationSyncFailure.class);
        app.setNotifyBaseUrl("https://cms.example.test?redirect=other");
        assertThatThrownBy(() -> policy.target(app)).isInstanceOf(ApplicationSyncFailure.class);
        app.setNotifyBaseUrl("https://cms.example.test");
        app.setSecretState("revoked");
        assertThatThrownBy(() -> policy.target(app)).isInstanceOf(ApplicationSyncFailure.class);
    }
}
