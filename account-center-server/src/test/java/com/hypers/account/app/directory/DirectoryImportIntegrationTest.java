package com.hypers.account.app.directory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.command.SaveApplicationCommand;
import com.hypers.account.app.command.SaveUserCommand;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.model.AccountUser;
import com.hypers.account.app.service.ApplicationManagementService;
import com.hypers.account.app.service.UserManagementService;

import com.hypers.account.auth.AdminAuthorizationService;
import com.hypers.account.contract.directory.AccountDirectoryOrganization;
import com.hypers.account.mapper.DirectoryImportMapper;
import com.hypers.account.contract.directory.AccountDirectoryRole;
import com.hypers.account.contract.directory.AccountDirectoryUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:directory_import;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "account.oidc.enabled=false", "account.application-sync.enabled=false"
})
class DirectoryImportIntegrationTest {
    @Autowired private UserManagementService userService;
    
    @Autowired private ApplicationManagementService applicationService;
    @Autowired private DirectoryImportService service;
    @Autowired private DirectoryImportMapper imports;
    @Autowired private AdminAuthorizationService authorization;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private ApplicationDirectoryClient client;
    private AccountUser operator;
    private AccountUser existing;
    private AccountApplication application;
    private ApplicationDirectorySnapshot snapshot;
    private String freshAccount;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        operator = userService.createManagedUser(new SaveUserCommand("operator-" + suffix, "", "Operator", ""), null);
        existing = userService.createManagedUser(new SaveUserCommand("existing-" + suffix, "", "Original", ""), null);
        userService.updatePassword(existing.getId(), "synthetic-existing-password-hash");
        userService.changeManagedUserStatus(existing.getId(), "disabled", userService.getUser(existing.getId()).getVersion(), null);
        freshAccount = "fresh-" + suffix;
        application = applicationService.createManagedApplication(new SaveApplicationCommand("directory-" + suffix, "CMS",
                "http://localhost:8850", "http://localhost:8850/callback", "", "http://localhost:9101", "default", "synthetic-app-secret", 0), operator.getId());
        snapshot = new ApplicationDirectorySnapshot();
        snapshot.setAppCode(application.getAppCode());
        snapshot.setCapturedAt(Instant.now());
        snapshot.setUsers(List.of(user("9007199254740993", existing.getAccount(), "enabled"),
                user("9007199254740994", freshAccount, "disabled")));
        AccountDirectoryRole role = new AccountDirectoryRole();
        role.setLocalRoleId("10");
        role.setCode("ACCOUNT_ADMIN");
        role.setName("CMS source role");
        role.setStatus("enabled");
        snapshot.setRoles(List.of(role));
        AccountDirectoryOrganization org = new AccountDirectoryOrganization();
        org.setLocalOrganizationId("20");
        org.setName("Source organization");
        org.setStatus("enabled");
        snapshot.setOrganizations(List.of(org));
        when(client.fetch(any())).thenReturn(snapshot);
    }

    @Test
    void importPreservesExistingCredentialsAndStatusAndDoesNotGrantSourceAdminRole() {
        DirectoryImportPreview preview = service.preview(application.getAppCode(), operator.getId());
        assertThat(preview.getNewUsers()).isEqualTo(1);
        assertThat(preview.getExistingUsers()).isEqualTo(1);
        assertThat(userService.findByAccount(freshAccount)).isNull();

        DirectoryImportResult result = service.commit(application.getAppCode(), preview.getImportId(), operator.getId());
        assertThat(result.getCreatedUsers()).isEqualTo(1);
        assertThat(result.getReusedUsers()).isEqualTo(1);
        AccountUser retained = userService.getUser(existing.getId());
        assertThat(retained.getName()).isEqualTo("Original");
        assertThat(retained.getPassword()).isEqualTo("synthetic-existing-password-hash");
        assertThat(retained.getStatus()).isEqualTo("disabled");
        AccountUser created = userService.findByAccount(freshAccount);
        assertThat(created.getId()).isNotEqualTo("9007199254740994");
        assertThat(created.getStatus()).isEqualTo("disabled");
        assertThat(created.getPassword()).isNull();
        assertThat(authorization.findRoles(created.getId())).isEmpty();
        assertThat(imports.link(created.getId(), application.getAppCode()).getLocalUserId()).isEqualTo("9007199254740994");
        assertThat(service.latest(application.getAppCode()).getOrganizations()).hasSize(1);
        assertThat(service.commit(application.getAppCode(), preview.getImportId(), operator.getId()).getCreatedUsers()).isEqualTo(1);
        assertThat(imports.links(application.getAppCode())).hasSize(2);
    }

    @Test
    void secretRotationInvalidatesThePreviewBeforeAnyUsersAreWritten() {
        DirectoryImportPreview preview = service.preview(application.getAppCode(), operator.getId());
        applicationService.rotateManagedApplicationSecret(application.getAppCode(), "synthetic-rotated-secret",
                application.getVersion(), operator.getId());
        assertThatThrownBy(() -> service.commit(application.getAppCode(), preview.getImportId(), operator.getId()))
                .isInstanceOf(BusinessException.class);
        assertThat(userService.findByAccount(freshAccount)).isNull();
        assertThat(imports.links(application.getAppCode())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM account_directory_imports WHERE id = ?",
                String.class, preview.getImportId())).isEqualTo("preview");
    }

    @Test
    void stableSourceIdsSurviveAccountChangesAndCannotBeReassignedToAnotherIdentity() {
        DirectoryImportPreview first = service.preview(application.getAppCode(), operator.getId());
        service.commit(application.getAppCode(), first.getImportId(), operator.getId());
        snapshot.getUsers().get(0).setAccount("renamed-source-" + UUID.randomUUID());
        DirectoryImportPreview second = service.preview(application.getAppCode(), operator.getId());
        assertThat(second.getNewUsers()).isZero();
        service.commit(application.getAppCode(), second.getImportId(), operator.getId());
        assertThat(imports.link(existing.getId(), application.getAppCode()).getLocalUserId()).isEqualTo("9007199254740993");
        snapshot.getUsers().get(0).setAccount(freshAccount);
        assertThatThrownBy(() -> service.preview(application.getAppCode(), operator.getId())).isInstanceOf(BusinessException.class);
    }

    private static AccountDirectoryUser user(String id, String account, String status) {
        AccountDirectoryUser user = new AccountDirectoryUser();
        user.setLocalUserId(id);
        user.setAccount(account);
        user.setName("Source name");
        user.setStatus(status);
        user.setRoleIds(List.of("10"));
        user.setOrganizationIds(List.of("20"));
        return user;
    }
}
