package com.hypers.account.starter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hypers.account.contract.MenuPermissionProtocol;
import com.hypers.account.contract.application.MenuPermissionNode;
import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import com.hypers.account.starter.properties.AccountIntegrationProperties;
import com.hypers.account.starter.spi.AccountMenuPermissionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AccountMenuPermissionControllerTest {

    @Test
    void queriesAndNormalizesCatalogOrder() {
        AccountMenuPermissionController controller = controller(new FixedHandler(snapshot()));

        MenuPermissionSnapshot result = controller.query(
                MenuPermissionProtocol.VERSION, query());

        assertThat(result.getNodes()).extracting(MenuPermissionNode::getCode)
                .containsExactly("menu", "report:view");
        assertThat(result.getSelectedCodes()).containsExactly("report:view");
    }

    @Test
    void rejectsProtocolMismatchBeforeCallingHandler() {
        AccountMenuPermissionController controller = controller(new FixedHandler(snapshot()));

        assertThatThrownBy(() -> controller.query("unsupported", query()))
                .isInstanceOfSatisfying(AccountIntegrationException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UPGRADE_REQUIRED);
                    assertThat(exception.getCode()).isEqualTo("PROTOCOL_VERSION_UNSUPPORTED");
                });
    }

    @Test
    void rejectsDuplicateSelectedCodes() {
        AccountMenuPermissionController controller = controller(new FixedHandler(snapshot()));
        MenuPermissionReplaceCommand command = replaceCommand();
        command.setSelectedCodes(List.of("report:view", "report:view"));

        assertThatThrownBy(() -> controller.replace(
                MenuPermissionProtocol.VERSION, "idempotency-key-0001", command))
                .isInstanceOfSatisfying(AccountIntegrationException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getCode()).isEqualTo("PERMISSION_CODE_INVALID");
                });
    }

    @Test
    void mapsUnexpectedHandlerFailureToUnavailable() {
        AccountMenuPermissionController controller = controller(new AccountMenuPermissionHandler() {
            @Override
            public MenuPermissionSnapshot query(MenuPermissionQuery query) {
                throw new IllegalStateException("internal detail");
            }

            @Override
            public MenuPermissionSnapshot replace(String idempotencyKey,
                                                    MenuPermissionReplaceCommand command) {
                throw new IllegalStateException("internal detail");
            }
        });

        assertThatThrownBy(() -> controller.query(MenuPermissionProtocol.VERSION, query()))
                .isInstanceOfSatisfying(AccountIntegrationException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("PERMISSION_PROVIDER_UNAVAILABLE");
                    assertThat(exception).hasMessageNotContaining("internal detail");
                });
    }

    private AccountMenuPermissionController controller(AccountMenuPermissionHandler handler) {
        AccountIntegrationProperties properties = new AccountIntegrationProperties();
        properties.setAppCode("synthetic-app");
        return new AccountMenuPermissionController(properties, handler);
    }

    private MenuPermissionQuery query() {
        return MenuPermissionQuery.builder()
                .protocolVersion(MenuPermissionProtocol.VERSION)
                .appCode("synthetic-app")
                .globalUserId("user-1")
                .localUserId("local-1")
                .account("user.one")
                .locale("zh-CN")
                .build();
    }

    private MenuPermissionReplaceCommand replaceCommand() {
        return MenuPermissionReplaceCommand.builder()
                .protocolVersion(MenuPermissionProtocol.VERSION)
                .appCode("synthetic-app")
                .globalUserId("user-1")
                .localUserId("local-1")
                .account("user.one")
                .locale("zh-CN")
                .expectedCatalogRevision("catalog-1")
                .expectedPermissionRevision("permission-1")
                .selectedCodes(List.of("report:view"))
                .build();
    }

    private MenuPermissionSnapshot snapshot() {
        MenuPermissionNode action = MenuPermissionNode.builder()
                .code("report:view")
                .parentCode("menu")
                .nodeType("ACTION")
                .defaultName("查看报告")
                .localizedNames(Map.of("en-US", "View report"))
                .assignable(true)
                .sort(20)
                .build();
        MenuPermissionNode menu = MenuPermissionNode.builder()
                .code("menu")
                .nodeType("MENU")
                .defaultName("菜单")
                .localizedNames(Map.of())
                .assignable(true)
                .sort(10)
                .build();
        return MenuPermissionSnapshot.builder()
                .catalogRevision("catalog-1")
                .permissionRevision("permission-1")
                .assignmentMode(MenuPermissionProtocol.ASSIGNMENT_MODE_ADDITIVE)
                .nodes(List.of(action, menu))
                .selectedCodes(List.of("report:view"))
                .inheritedCodes(List.of("menu"))
                .build();
    }

    private record FixedHandler(MenuPermissionSnapshot snapshot) implements AccountMenuPermissionHandler {
        @Override
        public MenuPermissionSnapshot query(MenuPermissionQuery query) {
            return snapshot;
        }

        @Override
        public MenuPermissionSnapshot replace(String idempotencyKey,
                                               MenuPermissionReplaceCommand command) {
            return snapshot;
        }
    }
}
