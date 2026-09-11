package com.hypers.account.starter.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.hypers.account.contract.application.AccountUserSyncResult;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import com.hypers.account.starter.client.AccountAdminTicketClient;
import com.hypers.account.starter.client.AccountSsoClient;
import com.hypers.account.starter.spi.AccountUserSyncHandler;
import com.hypers.account.starter.spi.AccountMenuPermissionHandler;
import com.hypers.account.starter.web.AccountMenuPermissionController;
import com.hypers.account.starter.web.AccountUserSyncController;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class AccountIntegrationAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    AccountIntegrationAutoConfiguration.class))
            .withPropertyValues(
                    "account.integration.enabled=true",
                    "account.integration.account-base-url=http://127.0.0.1:8088",
                    "account.integration.app-code=synthetic-app",
                    "account.integration.secret=synthetic-secret");

    @Test
    void createsOutboundClientsWithoutPublishingProviderWhenHandlerIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AccountSsoClient.class);
            assertThat(context).hasSingleBean(AccountAdminTicketClient.class);
            assertThat(context).doesNotHaveBean(AccountUserSyncController.class);
        });
    }

    @Test
    void publishesProviderWhenBusinessApplicationImplementsHandler() {
        contextRunner.withBean(AccountUserSyncHandler.class, () -> desiredState -> AccountUserSyncResult.builder()
                        .appCode(desiredState.getAppCode())
                        .globalUserId(desiredState.getGlobalUserId())
                        .localUserId("local-" + desiredState.getGlobalUserId())
                        .appliedStatus(desiredState.getDesiredStatus())
                        .appliedVersion(desiredState.getSyncVersion())
                        .resultCode("APPLIED")
                        .build())
                .run(context -> assertThat(context).hasSingleBean(AccountUserSyncController.class));
    }

    @Test
    void publishesMenuProviderWithoutRequiringUserSyncHandler() {
        contextRunner.withBean(AccountMenuPermissionHandler.class, () -> new AccountMenuPermissionHandler() {
                    @Override
                    public MenuPermissionSnapshot query(
                            com.hypers.account.contract.application.MenuPermissionQuery query) {
                        return new MenuPermissionSnapshot();
                    }

                    @Override
                    public MenuPermissionSnapshot replace(
                            String idempotencyKey,
                            com.hypers.account.contract.application.MenuPermissionReplaceCommand command) {
                        return new MenuPermissionSnapshot();
                    }
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(AccountMenuPermissionController.class);
                    assertThat(context).doesNotHaveBean(AccountUserSyncController.class);
                });
    }

    @Test
    void keepsDedicatedHttpClientWhenApplicationDefinesAnotherHttpClient() {
        contextRunner.withBean("businessHttpClient", HttpClient.class, HttpClient::newHttpClient)
                .run(context -> {
                    assertThat(context).hasBean("accountIntegrationHttpClient");
                    assertThat(context).hasSingleBean(AccountSsoClient.class);
                });
    }
}
