package com.hypers.account.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AccountDirectoryServiceTest {
    @Test
    void creatingUsersDoesNotGrantApplicationsAndUnconfiguredApplicationsDoNotSynchronize() {
        var service = new AccountDirectoryService(new InMemoryAccountStore());
        service.registerApplication(application(""));
        var user = service.createUser(new SaveUserCommand("alice", "alice@example.com", "Alice", "123"));
        assertThat(service.isAuthorized(user.getId(), "cms-ai")).isFalse();
        service.authorize(user.getId(), "cms-ai");
        assertThat(service.isAuthorized(user.getId(), "cms-ai")).isTrue();
        service.updateUser(user.getId(), new SaveUserCommand("alice", "alice@example.com", "Changed", "123"));
        assertThat(service.getUser(user.getId()).getName()).isEqualTo("Changed");
        service.deauthorize(user.getId(), "cms-ai");
        assertThat(service.isAuthorized(user.getId(), "cms-ai")).isFalse();
    }

    @Test
    void disabledUsersCannotUseGrantedApplications() {
        var service = new AccountDirectoryService(new InMemoryAccountStore());
        service.registerApplication(application(""));
        var user = service.createUser(new SaveUserCommand("alice", "alice@example.com", "Alice", "123"));
        service.authorize(user.getId(), "cms-ai");
        service.disableUser(user.getId());
        assertThat(service.isAuthorized(user.getId(), "cms-ai")).isFalse();
    }

    @Test
    void configuredProvidersRequireTheConfirmedAuthorizationWorkflow() {
        var service = new AccountDirectoryService(new InMemoryAccountStore());
        service.registerApplication(application("http://localhost:9101"));
        var user = service.createUser(new SaveUserCommand("alice", "alice@example.com", "Alice", "123"));
        assertThatThrownBy(() -> service.authorize(user.getId(), "cms-ai"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.isAuthorized(user.getId(), "cms-ai")).isFalse();
    }

    @Test
    void availableFunctionsFollowConfigurationAndClearWhenConfigurationIsRemoved() {
        var app = new AccountApplication("cms-ai", "CMS", "", "", "", "", "synthetic-secret", "default");
        assertThat(app.getProtocolCapabilities()).isEmpty();
        app.setSsoCallbackUrl("https://cms.example.test/callback");
        assertThat(app.getProtocolCapabilities()).isEqualTo("sso");
        app.setNotifyBaseUrl("https://cms.example.test");
        assertThat(app.getProtocolCapabilities()).contains("directory", "user_sync", "menu_permission_v1");
        app.setNotifyBaseUrl("");
        assertThat(app.getProtocolCapabilities()).isEqualTo("sso");
    }

    @Test
    void secretRotationAndUserSearchRetainTheirBusinessBehavior() {
        var service = new AccountDirectoryService(new InMemoryAccountStore());
        service.registerApplication(application(""));
        service.createUser(new SaveUserCommand("alice", "alice@example.com", "Alice", "123"));
        service.createUser(new SaveUserCommand("bob", "bob@example.com", "Bob", "456"));
        String secret = service.rotateApplicationSecret("cms-ai");
        assertThat(secret).isNotBlank().isNotEqualTo("synthetic-secret");
        assertThat(service.getApplication("cms-ai").getSecretVersion()).isEqualTo(2);
        assertThat(service.findUsers("alice", null)).hasSize(1);
        assertThat(service.findUsers(null, null)).hasSize(2);
    }

    private RegisterApplicationCommand application(String serviceUrl) {
        return new RegisterApplicationCommand("cms-ai", "CMS", "https://cms.example.test",
                "https://cms.example.test/callback", "", serviceUrl, "synthetic-secret", "default");
    }
}
