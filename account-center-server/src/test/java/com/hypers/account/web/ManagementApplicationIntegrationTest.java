package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.app.SaveApplicationCommand;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.web.management.CsrfTokenManager;
import com.hypers.account.web.management.ManagementApplicationWriteService.CreatedApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManagementApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountDirectoryService directoryService;

    @Autowired
    private AdminRoleMapper adminRoleMapper;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void grantAdminRole() {
        adminRoleMapper.insert("admin-user", "ACCOUNT_ADMIN");
    }

    @Test
    void applicationLifecycleReturnsSecretOnceAndProtectsVersionedChanges() throws Exception {
        ManagementSession management = managementSession(adminSession());
        String body = applicationBody("managed-app", "http://localhost:9100/callback");

        MvcResult created = mockMvc.perform(post("/api/applications")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "managed-app-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.application.appCode").value("managed-app"))
                .andExpect(jsonPath("$.application.version").value(1))
                .andExpect(jsonPath("$.application.secret").doesNotExist())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andReturn();
        String firstSecret = read(created, "secret");
        assertThat(jdbcTemplate.queryForObject(
                "select response_body from account_idempotency_records where idempotency_key = ?",
                String.class, "managed-app-create")).isEqualTo("{}");

        mockMvc.perform(post("/api/applications")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "managed-app-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_RESULT_NOT_REPLAYABLE"))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString(firstSecret))));

        mockMvc.perform(get("/api/applications/managed-app")
                        .session(management.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appCode").value("managed-app"))
                .andExpect(jsonPath("$.secret").doesNotExist());

        mockMvc.perform(put("/api/applications/managed-app")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "managed-app-stale-update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(99)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));

        MvcResult rotated = mockMvc.perform(post("/api/applications/managed-app/secret/rotate")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "managed-app-rotate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"reason\":\"定期轮换\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.version").value(2))
                .andExpect(jsonPath("$.application.secretVersion").value(2))
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andReturn();
        assertThat(read(rotated, "secret")).isNotEqualTo(firstSecret);

        mockMvc.perform(post("/api/applications/managed-app/secret/revoke")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "managed-app-revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"reason\":\"停止接入\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secretState").value("revoked"))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.secret").doesNotExist());

        assertThat(auditLogService.search("admin-user", null, "APPLICATION", "managed-app"))
                .hasSize(3);
    }

    @Test
    void registrationRejectsWildcardFragmentUserInfoAndUnsupportedSchemes() throws Exception {
        ManagementSession management = managementSession(adminSession());
        String[] invalidCallbacks = {
                "https://*.example.com/callback",
                "https://example.com/callback#fragment",
                "https://user@example.com/callback",
                "file:///tmp/callback"
        };

        for (int index = 0; index < invalidCallbacks.length; index++) {
            mockMvc.perform(post("/api/applications")
                            .session(management.session())
                            .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                            .header("Idempotency-Key", "invalid-url-" + index)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(applicationBody("invalid-app-" + index, invalidCallbacks[index])))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void protocolCapabilitiesRejectNullAndDuplicateValues() throws Exception {
        ManagementSession management = managementSession(adminSession());
        for (String protocols : new String[] {"[null]", "[\"sso\",\"sso\"]", "[]", "[\"unknown\"]"}) {
            ObjectNode body = (ObjectNode) objectMapper.readTree(
                    applicationBody("invalid-protocol", "https://example.com/callback"));
            body.set("protocolCapabilities", objectMapper.readTree(protocols));
            mockMvc.perform(post("/api/applications")
                            .session(management.session())
                            .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                            .header("Idempotency-Key", "invalid-protocol")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void registrationAcceptsMenuPermissionCapability() throws Exception {
        ManagementSession management = managementSession(adminSession());
        ObjectNode body = (ObjectNode) objectMapper.readTree(
                applicationBody("menu-provider", "https://example.com/callback"));
        body.set("protocolCapabilities", objectMapper.readTree(
                "[\"sso\",\"admin_ticket\",\"user_sync\",\"menu_permission_v1\"]"));

        mockMvc.perform(post("/api/applications")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "menu-provider-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.protocolCapabilities")
                        .value(hasItem("menu_permission_v1")));
    }

    @Test
    void sensitiveValueObjectsDoNotGenerateSecretToString() {
        String secret = "synthetic-sensitive-value";
        SaveApplicationCommand command = new SaveApplicationCommand(
                "safe-app", "应用", "https://example.com", "https://example.com/callback",
                "https://example.com/permissions", "https://example.com/notify",
                "default", "sso", secret, 0);
        assertThat(command.toString()).doesNotContain(secret);
        assertThat(new CreatedApplication(null, secret).toString()).doesNotContain(secret);
    }

    @Test
    void accessChangeIsPendingAdaptationIdempotentAndBlockedForDisabledApplications() throws Exception {
        ManagementSession management = managementSession(adminSession());
        createApplication(management, "access-app", "access-app-create");
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "access-user", "access@example.com", "准入用户", "13800000111"));

        mockMvc.perform(get("/api/users/" + user.getId() + "/application-access")
                        .session(management.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appCode").value("access-app"))
                .andExpect(jsonPath("$[0].desiredStatus").value("disabled"))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].integrationStatus")
                        .value("pending_application_adaptation"));

        MvcResult changed = mockMvc.perform(put("/api/users/" + user.getId()
                                + "/application-access/access-app")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "access-enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"enabled\",\"version\":0,\"reason\":\"开通业务访问\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.desiredStatus").value("enabled"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.integrationStatus")
                        .value("pending_application_adaptation"))
                .andExpect(jsonPath("$.syncCommandId").isNotEmpty())
                .andReturn();
        String syncCommandId = read(changed, "syncCommandId");

        mockMvc.perform(get("/api/users/" + user.getId() + "/applications")
                        .session(management.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appCode").value("access-app"))
                .andExpect(jsonPath("$[0].secret").doesNotExist());

        mockMvc.perform(put("/api/users/" + user.getId() + "/application-access/access-app")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "access-missing-version")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\",\"reason\":\"缺少版本\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/users/" + user.getId() + "/application-access/access-app")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "access-enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"enabled\",\"version\":0,\"reason\":\"开通业务访问\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.syncCommandId").value(syncCommandId));

        mockMvc.perform(put("/api/applications/access-app/status")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "access-app-disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\",\"version\":1,\"reason\":\"维护停用\"}"))
                .andExpect(status().isOk());

        AccountUser blockedUser = directoryService.createUser(new SaveUserCommand(
                "blocked-user", "blocked@example.com", "禁用应用用户", "13800000112"));
        mockMvc.perform(put("/api/users/" + blockedUser.getId()
                                + "/application-access/access-app")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "blocked-access-enable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"enabled\",\"version\":0,\"reason\":\"不应成功\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPLICATION_DISABLED"));

        AccountUser auditor = directoryService.createUser(new SaveUserCommand(
                "access-auditor", "auditor@example.com", "准入审计员", "13800000113"));
        adminRoleMapper.insert(auditor.getId(), "ACCOUNT_AUDITOR");
        ManagementSession auditSession = managementSession(
                new AccountSessionUser(auditor.getId(), auditor.getAccount(), auditor.getName()));
        mockMvc.perform(put("/api/users/" + user.getId() + "/application-access/access-app")
                        .session(auditSession.session())
                        .header(CsrfTokenManager.HEADER_NAME, auditSession.csrfToken())
                        .header("Idempotency-Key", "auditor-access-write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\",\"version\":1,\"reason\":\"只读角色\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void menuPermissionManagementRequiresAdminSessionAndCsrf() throws Exception {
        String path = "/api/users/user-1/applications/app-1/menu-permissions";
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        AccountUser auditor = directoryService.createUser(new SaveUserCommand(
                "menu-auditor", "menu-auditor@example.com", "菜单审计员", "13800000114"));
        adminRoleMapper.insert(auditor.getId(), "ACCOUNT_AUDITOR");
        ManagementSession auditSession = managementSession(
                new AccountSessionUser(auditor.getId(), auditor.getAccount(), auditor.getName()));
        mockMvc.perform(get(path).session(auditSession.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        ManagementSession admin = managementSession(adminSession());
        mockMvc.perform(put(path)
                        .session(admin.session())
                        .header("Idempotency-Key", "menu-permission-csrf-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedAccessVersion\":1,\"expectedCatalogRevision\":\"catalog-1\","
                                + "\"expectedPermissionRevision\":\"permission-1\",\"selectedCodes\":[]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    private void createApplication(
            ManagementSession management, String appCode, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/api/applications")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody(appCode, "http://localhost:9100/callback")))
                .andExpect(status().isCreated());
    }

    private String applicationBody(String appCode, String callbackUrl) {
        return "{\"appCode\":\"" + appCode + "\",\"name\":\"测试应用\","
                + "\"entryUrl\":\"http://localhost:9100\","
                + "\"ssoCallbackUrl\":\"" + callbackUrl + "\","
                + "\"permissionIframeUrl\":\"http://localhost:9100/permissions\","
                + "\"notifyBaseUrl\":\"http://localhost:9100/notify\","
                + "\"defaultTenantCode\":\"default\","
                + "\"protocolCapabilities\":[\"sso\",\"admin_ticket\",\"user_sync\"]}";
    }

    private String updateBody(long version) {
        return "{\"name\":\"更新应用\",\"entryUrl\":\"http://localhost:9100\","
                + "\"ssoCallbackUrl\":\"http://localhost:9100/callback\","
                + "\"permissionIframeUrl\":\"http://localhost:9100/permissions\","
                + "\"notifyBaseUrl\":\"http://localhost:9100/notify\","
                + "\"defaultTenantCode\":\"default\","
                + "\"protocolCapabilities\":[\"sso\"],\"version\":" + version + "}";
    }

    private ManagementSession managementSession(AccountSessionUser user) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        session.setAttribute(AuthController.SESSION_USER_KEY, user);
        return new ManagementSession(session, read(result, "csrfToken"));
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get(field).asText();
    }

    private AccountSessionUser adminSession() {
        return new AccountSessionUser("admin-user", "admin", "管理员");
    }

    private record ManagementSession(MockHttpSession session, String csrfToken) {
    }
}
