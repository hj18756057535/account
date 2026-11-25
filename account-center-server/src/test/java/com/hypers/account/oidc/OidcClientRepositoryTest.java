package com.hypers.account.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.service.ApplicationManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class OidcClientRepositoryTest {
    @Test
    void pageManagedSecretWorksWithoutClientConfigurationAndRotationRevokesThePreviousSecret() {
        ApplicationManagementService applicationService = mock(ApplicationManagementService.class);
        AccountApplication app = new AccountApplication("cms-ai", "CMS", "http://localhost:8850",
                "http://localhost:8850/api-test/main/login/account/callback", "http://localhost:8850",
                "http://localhost:9101", "synthetic-first-secret", "default");
        when(applicationService.getApplication("cms-ai")).thenReturn(app);
        OidcClientRepository repository = new OidcClientRepository(applicationService, new OidcProperties());
        var encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches("synthetic-first-secret",
                repository.findByClientId("cms-ai").getClientSecret().substring(8))).isTrue();

        app.setSecret("synthetic-rotated-secret");
        String encoded = repository.findByClientId("cms-ai").getClientSecret().substring(8);
        assertThat(encoder.matches("synthetic-first-secret", encoded)).isFalse();
        assertThat(encoder.matches("synthetic-rotated-secret", encoded)).isTrue();
        app.setSecretState("revoked");
        assertThat(repository.findByClientId("cms-ai")).isNull();
    }
}
