package com.hypers.account.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.contract.application.MenuPermissionNode;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import com.hypers.account.web.management.ApiException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ApplicationMenuPermissionServiceTest {

    private final AccountDirectoryService directory = mock(AccountDirectoryService.class);
    private final ApplicationSyncStore syncStore = mock(ApplicationSyncStore.class);
    private final ApplicationMenuPermissionClient client = mock(ApplicationMenuPermissionClient.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final ApplicationMenuPermissionService service = new ApplicationMenuPermissionService(
            directory, syncStore, client, audit, new ObjectMapper().findAndRegisterModules());

    @BeforeEach
    void setUp() {
        when(directory.getUser("admin")).thenReturn(user("admin", "administrator"));
        when(directory.getUser("user-1")).thenReturn(user("user-1", "alice"));
        AccountApplication application = new AccountApplication("app-1", "Application",
                "https://app.example", "https://app.example/callback", "https://app.example/permissions",
                "https://app.example", "secret", "default");
        application.setProtocolCapabilities("user_sync,menu_permission_v1");
        when(directory.getApplication("app-1")).thenReturn(application);
        when(directory.getUserApplicationAccess("user-1", "app-1")).thenReturn(new ApplicationAccess(
                "user-1", "app-1", "enabled", 7, "succeeded", "command-1", null,
                "enabled", 7L, null, null, false));
        when(syncStore.lockApplication("app-1")).thenReturn(Boolean.TRUE);
        when(syncStore.lockAccess("user-1", "app-1")).thenReturn(new ApplicationAccess(
                "user-1", "app-1", "enabled", 7, null, null, null));
        ApplicationUserState state = new ApplicationUserState();
        state.setUserId("user-1");
        state.setAppCode("app-1");
        state.setLocalUserId("local-1");
        state.setAppliedStatus("enabled");
        state.setAppliedVersion(7);
        when(syncStore.state("user-1", "app-1")).thenReturn(state);
    }

    @Test
    void replaceUsesMappedIdentityAndAuditsOnlySelectionSummary() {
        when(client.replace(any(), eq("idem-1"), any())).thenReturn(snapshot());

        var result = service.replace("user-1", "app-1", 7, "catalog-1", "permission-1",
                List.of("menu.secret-value"), "zh-CN", "idem-1", "admin");

        assertThat(result.getAccessVersion()).isEqualTo(7);
        ArgumentCaptor<MenuPermissionReplaceCommand> command = ArgumentCaptor.forClass(MenuPermissionReplaceCommand.class);
        verify(client).replace(any(), eq("idem-1"), command.capture());
        assertThat(command.getValue().getGlobalUserId()).isEqualTo("user-1");
        assertThat(command.getValue().getLocalUserId()).isEqualTo("local-1");
        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        verify(audit).log(eq("admin"), eq("APPLICATION_MENU_PERMISSION_REPLACED"),
                eq("USER_APPLICATION"), eq("user-1:app-1"), detail.capture());
        assertThat(detail.getValue()).contains("\"selectedCount\":1", "selectionDigest")
                .doesNotContain("menu.secret-value");
    }

    @Test
    void blocksProviderCallUntilUserSyncIsConfirmed() {
        when(directory.getUserApplicationAccess("user-1", "app-1")).thenReturn(new ApplicationAccess(
                "user-1", "app-1", "enabled", 7, "succeeded", "command-1", null,
                "enabled", 6L, null, null, false));

        assertThatThrownBy(() -> service.query("user-1", "app-1", "zh-CN", "admin"))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("MENU_PERMISSION_PREREQUISITE_NOT_MET");
        verify(client, never()).query(any(), any());
    }

    @Test
    void mapsProviderConflictToStableManagementError() {
        when(client.replace(any(), any(), any()))
                .thenThrow(new ApplicationMenuPermissionFailure(409, "PERMISSION_REVISION_CONFLICT"));

        assertThatThrownBy(() -> service.replace("user-1", "app-1", 7, "catalog-1", "permission-1",
                List.of(), "en-US", "idem-1", "admin"))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    ApiException api = (ApiException) error;
                    assertThat(api.getStatus().value()).isEqualTo(409);
                    assertThat(api.getCode()).isEqualTo("PERMISSION_REVISION_CONFLICT");
                    assertThat(api.getMessage()).isEqualTo("menuPermission.revisionConflict");
                });
    }

    private AccountUser user(String id, String account) {
        return new AccountUser(id, account, account + "@example.com", account, null);
    }

    private MenuPermissionSnapshot snapshot() {
        return MenuPermissionSnapshot.builder()
                .catalogRevision("catalog-1")
                .permissionRevision("permission-2")
                .assignmentMode("ADDITIVE")
                .nodes(List.of(MenuPermissionNode.builder().code("menu.secret-value").nodeType("MENU")
                        .defaultName("Menu").localizedNames(Map.of()).assignable(true).sort(1).build()))
                .selectedCodes(List.of("menu.secret-value"))
                .inheritedCodes(List.of())
                .build();
    }
}
