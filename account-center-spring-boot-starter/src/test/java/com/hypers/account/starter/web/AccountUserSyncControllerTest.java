package com.hypers.account.starter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hypers.account.contract.application.AccountUserDesiredState;
import com.hypers.account.contract.application.AccountUserSyncResult;
import com.hypers.account.starter.properties.AccountIntegrationProperties;
import com.hypers.account.starter.spi.AccountUserSyncHandler;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AccountUserSyncControllerTest {

    @Test
    void forwardsIdempotencyKeyToDurableHandlerEntryPoint() {
        AccountUserSyncHandler handler = new AccountUserSyncHandler() {
            @Override
            public AccountUserSyncResult apply(AccountUserDesiredState state) {
                throw new AssertionError("Legacy entry point must not be selected");
            }

            @Override
            public AccountUserSyncResult apply(String key, AccountUserDesiredState state) {
                assertThat(key).isEqualTo("durable-command-1");
                return AccountUserSyncResult.builder().appCode(state.getAppCode()).globalUserId(state.getGlobalUserId())
                        .localUserId("local-durable-1").appliedStatus(state.getDesiredStatus())
                        .appliedVersion(state.getSyncVersion()).resultCode("APPLIED").build();
            }
        };
        var controller = new AccountUserSyncController(properties(), handler, new AccountUserSyncGuard());
        assertThat(controller.apply("user-1", "durable-command-1", desiredState(1, "enabled")).getLocalUserId())
                .isEqualTo("local-durable-1");
    }

    @Test
    void reusesIdempotentResultAndRejectsStaleVersion() {
        AtomicInteger calls = new AtomicInteger();
        AccountUserSyncHandler handler = desiredState -> {
            calls.incrementAndGet();
            return AccountUserSyncResult.builder()
                    .appCode(desiredState.getAppCode())
                    .globalUserId(desiredState.getGlobalUserId())
                    .localUserId("local-user-1")
                    .appliedStatus(desiredState.getDesiredStatus())
                    .appliedVersion(desiredState.getSyncVersion())
                    .resultCode("APPLIED")
                    .build();
        };
        AccountUserSyncController controller = new AccountUserSyncController(
                properties(), handler, new AccountUserSyncGuard());
        AccountUserDesiredState state = desiredState(2, "enabled");

        AccountUserSyncResult first = controller.apply("user-1", "request-1", state);
        AccountUserSyncResult duplicate = controller.apply("user-1", "request-1", state);

        assertThat(duplicate).isSameAs(first);
        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> controller.apply("user-1", "request-2", desiredState(1, "disabled")))
                .isInstanceOf(AccountIntegrationException.class)
                .extracting("code")
                .isEqualTo("RESOURCE_VERSION_CONFLICT");
    }

    @Test
    void rejectsUnknownDesiredStatusBeforeCallingHandler() {
        AccountUserSyncController controller = new AccountUserSyncController(
                properties(), desiredState -> null, new AccountUserSyncGuard());

        assertThatThrownBy(() -> controller.apply("user-1", "request-1", desiredState(1, "unknown")))
                .isInstanceOf(AccountIntegrationException.class)
                .extracting("code")
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void mapsBusinessHandlerFailureToStableDependencyError() {
        AccountUserSyncController controller = new AccountUserSyncController(
                properties(),
                desiredState -> {
                    throw new IllegalStateException("database details must not escape");
                },
                new AccountUserSyncGuard());

        assertThatThrownBy(() -> controller.apply("user-1", "request-1", desiredState(1, "enabled")))
                .isInstanceOf(AccountIntegrationException.class)
                .hasMessage("业务系统未能应用用户同步请求")
                .extracting("code")
                .isEqualTo("DEPENDENCY_UNAVAILABLE");
    }

    private AccountIntegrationProperties properties() {
        AccountIntegrationProperties properties = new AccountIntegrationProperties();
        properties.setAccountBaseUrl("http://127.0.0.1:8088");
        properties.setAppCode("synthetic-app");
        properties.setSecret("synthetic-secret");
        return properties;
    }

    private AccountUserDesiredState desiredState(long version, String status) {
        return AccountUserDesiredState.builder()
                .appCode("synthetic-app")
                .globalUserId("user-1")
                .account("user.one")
                .displayName("合成用户")
                .tenantCode("default")
                .desiredStatus(status)
                .syncVersion(version)
                .occurredAt(Instant.parse("2026-08-24T08:00:00Z"))
                .build();
    }
}
