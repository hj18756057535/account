package com.hypers.account.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountDirectoryServiceTest {

    @Test
    void userIsSyncedOnlyAfterApplicationAuthorizationAndUpdatesReachAuthorizedApps() {
        RecordingSyncClient syncClient = new RecordingSyncClient();
        AccountDirectoryService service = new AccountDirectoryService(new InMemoryAccountStore(), syncClient);
        AccountApplication application = service.registerApplication(new RegisterApplicationCommand(
                "cms-ai",
                "双碳服务",
                "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003",
                "secret",
                "default"));

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
                "cms-ai",
                "双碳服务",
                "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003",
                "secret",
                "default"));
        AccountUser user = service.createUser(new SaveUserCommand("zhangsan", "zhangsan@example.com", "张三", "13800000000"));
        service.authorize(user.getId(), "cms-ai");

        service.deauthorize(user.getId(), "cms-ai");

        assertThat(syncClient.events).contains("disable:cms-ai:zhangsan:default");
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
