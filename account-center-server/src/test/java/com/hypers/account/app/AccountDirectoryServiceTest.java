package com.hypers.account.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountDirectoryServiceTest {

    @Test
    void userIsSyncedOnlyAfterApplicationAuthorizationAndUpdatesReachAuthorizedApps() {
        RecordingSyncClient syncClient = new RecordingSyncClient();
        AccountDirectoryService service = new AccountDirectoryService(new InMemoryAccountStore(), syncClient);
        AccountApplication application = service.registerApplication(new RegisterApplicationCommand(
                "cms-ai", "双碳服务", "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003", "secret", "default"));

        AccountUser user = service.createUser(new SaveUserCommand("zhangsan", "zhangsan@example.com", "张三", "13800000000"));

        assertThat(syncClient.events).isEmpty();

        service.authorize(user.getId(), application.getAppCode());

        assertThat(syncClient.events).containsExactly("upsert:cms-ai:zhangsan:default");

        service.updateUser(user.getId(), new SaveUserCommand("zhangsan", "new@example.com", "张三三", "13800000000"));

        assertThat(syncClient.events).containsExactly(
                "upsert:cms-ai:zhangsan:default",
                "upsert:cms-ai:zhangsan:default");
    }

    @Test
    void deauthorizingApplicationDisablesUserInThatApplication() {
        RecordingSyncClient syncClient = new RecordingSyncClient();
        AccountDirectoryService service = new AccountDirectoryService(new InMemoryAccountStore(), syncClient);
        service.registerApplication(new RegisterApplicationCommand(
                "cms-ai", "双碳服务", "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003", "secret", "default"));
        AccountUser user = service.createUser(new SaveUserCommand("zhangsan", "zhangsan@example.com", "张三", "13800000000"));
        service.authorize(user.getId(), "cms-ai");

        service.deauthorize(user.getId(), "cms-ai");

        assertThat(syncClient.events).contains("disable:cms-ai:zhangsan:default");
    }

    @Test
    void disablingApplicationPreventsAuthorization() {
        RecordingSyncClient syncClient = new RecordingSyncClient();
        AccountDirectoryService service = new AccountDirectoryService(new InMemoryAccountStore(), syncClient);
        service.registerApplication(new RegisterApplicationCommand(
                "cms-ai", "双碳服务", "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003", "secret", "default"));
        AccountUser user = service.createUser(new SaveUserCommand("zhangsan", "zhangsan@example.com", "张三", "13800000000"));
        service.disableApplication("cms-ai");

        assertThatThrownBy(() -> service.authorize(user.getId(), "cms-ai"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void disablingUserSyncsDisableToAllAuthorizedApplications() {
        RecordingSyncClient syncClient = new RecordingSyncClient();
        AccountDirectoryService service = new AccountDirectoryService(new InMemoryAccountStore(), syncClient);
        service.registerApplication(new RegisterApplicationCommand(
                "cms-ai", "双碳服务", "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003", "secret", "default"));
        AccountUser user = service.createUser(new SaveUserCommand("zhangsan", "zhangsan@example.com", "张三", "13800000000"));
        service.authorize(user.getId(), "cms-ai");
        syncClient.events.clear();

        service.disableUser(user.getId());

        assertThat(syncClient.events).containsExactly("disable:cms-ai:zhangsan:default");
    }

    @Test
    void rotateSecretGeneratesNewSecretAndIncrementsVersion() {
        AccountDirectoryService service = new AccountDirectoryService(new InMemoryAccountStore(), new RecordingSyncClient());
        service.registerApplication(new RegisterApplicationCommand(
                "cms-ai", "双碳服务", "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003", "secret", "default"));

        String newSecret = service.rotateApplicationSecret("cms-ai");

        assertThat(newSecret).isNotBlank().isNotEqualTo("secret");
        AccountApplication app = service.getApplication("cms-ai");
        assertThat(app.getSecret()).isEqualTo(newSecret);
        assertThat(app.getSecretVersion()).isEqualTo(2);
    }

    @Test
    void findUsersAndApplicationsReturnFilteredResults() {
        AccountDirectoryService service = new AccountDirectoryService(new InMemoryAccountStore(), new RecordingSyncClient());
        service.createUser(new SaveUserCommand("zhangsan", "zhangsan@example.com", "张三", "13800000000"));
        service.createUser(new SaveUserCommand("lisi", "lisi@example.com", "李四", "13900000000"));
        service.registerApplication(new RegisterApplicationCommand(
                "cms-ai", "双碳服务", "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003", "secret", "default"));

        assertThat(service.findUsers("zhang", null)).hasSize(1);
        assertThat(service.findUsers(null, null)).hasSize(2);
        assertThat(service.findUsers("nonexist", null)).isEmpty();
        assertThat(service.findApplications(null, null)).hasSize(1);
    }

    private static class RecordingSyncClient implements ApplicationUserSyncClient {

        private final List<String> events = new ArrayList<>();

        @Override
        public void upsert(AccountApplication application, AccountUser user) {
            events.add("upsert:" + application.getAppCode() + ":" + user.getAccount() + ":" + application.getDefaultTenantCode());
        }

        @Override
        public void disable(AccountApplication application, AccountUser user) {
            events.add("disable:" + application.getAppCode() + ":" + user.getAccount() + ":" + application.getDefaultTenantCode());
        }
    }
}
