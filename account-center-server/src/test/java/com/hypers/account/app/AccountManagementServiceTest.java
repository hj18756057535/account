package com.hypers.account.app;

import com.hypers.account.app.command.RegisterApplicationCommand;
import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.command.SaveUserCommand;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.service.ApplicationManagementService;
import com.hypers.account.app.service.UserApplicationAccessService;
import com.hypers.account.app.service.UserManagementService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AccountManagementServiceTest {

    @Autowired
    private UserManagementService userService;

    @Autowired
    private ApplicationManagementService applicationService;

    @Autowired
    private UserApplicationAccessService accessService;

    @Test
    void creatingUsersDoesNotGrantApplicationsAndUnconfiguredApplicationsDoNotSynchronize() {
        String appCode = applicationService.registerApplication(application("")).getAppCode();
        String suffix = suffix();
        var user = userService.createUser(new SaveUserCommand("alice-" + suffix, "alice@example.com", "Alice", "123"));

        assertThat(accessService.isAuthorized(user.getId(), appCode)).isFalse();
        accessService.authorize(user.getId(), appCode);
        assertThat(accessService.isAuthorized(user.getId(), appCode)).isTrue();

        userService.updateManagedUser(user.getId(),
                new SaveUserCommand("alice-" + suffix, "alice@example.com", "Changed", "123"),
                user.getVersion(), null);
        assertThat(userService.getUser(user.getId()).getName()).isEqualTo("Changed");

        accessService.deauthorize(user.getId(), appCode);
        assertThat(accessService.isAuthorized(user.getId(), appCode)).isFalse();
    }

    @Test
    void disabledUsersCannotUseGrantedApplications() {
        String appCode = applicationService.registerApplication(application("")).getAppCode();
        var user = userService.createUser(new SaveUserCommand("alice-" + suffix(), "alice@example.com", "Alice", "123"));

        accessService.authorize(user.getId(), appCode);
        userService.changeManagedUserStatus(user.getId(), "disabled", user.getVersion(), null);

        assertThat(accessService.isAuthorized(user.getId(), appCode)).isFalse();
    }

    @Test
    void configuredProvidersRequireTheConfirmedAuthorizationWorkflow() {
        String appCode = applicationService.registerApplication(application("http://localhost:9101")).getAppCode();
        var user = userService.createUser(new SaveUserCommand("alice-" + suffix(), "alice@example.com", "Alice", "123"));

        assertThatThrownBy(() -> accessService.authorize(user.getId(), appCode))
                .isInstanceOf(BusinessException.class);
        assertThat(accessService.isAuthorized(user.getId(), appCode)).isFalse();
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
        String appCode = applicationService.registerApplication(application("")).getAppCode();
        String suffix = suffix();
        userService.createUser(new SaveUserCommand("alice-" + suffix, "alice@example.com", "Alice", "123"));
        userService.createUser(new SaveUserCommand("bob-" + suffix, "bob@example.com", "Bob", "456"));

        var current = applicationService.getApplication(appCode);
        var rotated = applicationService.rotateManagedApplicationSecret(
                appCode, "rotated-secret", current.getVersion(), null);
        assertThat(rotated.getSecret()).isNotBlank().isNotEqualTo("synthetic-secret");
        assertThat(rotated.getSecretVersion()).isEqualTo(2);
        assertThat(userService.findUsers("alice-" + suffix, null)).hasSize(1);
        assertThat(userService.findUsers(suffix, null)).hasSize(2);
    }

    private RegisterApplicationCommand application(String serviceUrl) {
        return new RegisterApplicationCommand("cms-" + suffix(), "CMS", "https://cms.example.test",
                "https://cms.example.test/callback", "", serviceUrl, "synthetic-secret", "default");
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
