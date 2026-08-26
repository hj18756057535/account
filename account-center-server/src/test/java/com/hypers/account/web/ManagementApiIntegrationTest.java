package com.hypers.account.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountStore;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.auth.AccountLoginService;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.web.management.CsrfTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManagementApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountDirectoryService directoryService;

    @Autowired
    private AccountStore accountStore;

    @Autowired
    private AccountLoginService loginService;

    @Autowired
    private AdminRoleMapper adminRoleMapper;

    @Autowired
    private AuditLogService auditLogService;

    @BeforeEach
    void grantExistingAdminRole() {
        adminRoleMapper.insert("admin-user", "ACCOUNT_ADMIN");
    }

    @Test
    void anonymousSessionInitializesCsrfWithoutExposingSessionId() throws Exception {
        mockMvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.capabilities").isEmpty())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty())
                .andExpect(jsonPath("$.sessionId").doesNotExist());
    }

    @Test
    void loginRequiresCsrfAndReturnsOnlyManagementCapabilities() throws Exception {
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "console-admin", "console-admin@example.com", "控制台管理员", "13800000001"));
        accountStore.setUserPassword(user.getId(), loginService.encodePassword("safe-password"));
        adminRoleMapper.insert(user.getId(), "ACCOUNT_ADMIN");

        MvcResult sessionResult = mockMvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) sessionResult.getRequest().getSession(false);
        String csrfToken = read(sessionResult, "csrfToken");

        mockMvc.perform(post("/api/session")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"console-admin\",\"password\":\"safe-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        mockMvc.perform(post("/api/session")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"console-admin\",\"password\":\"safe-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.account").value("console-admin"))
                .andExpect(jsonPath("$.roles[0]").value("ACCOUNT_ADMIN"))
                .andExpect(jsonPath("$.capabilities[0]").value("users:read"))
                .andExpect(jsonPath("$.capabilities[1]").value("users:write"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mockMvc.perform(delete("/api/session")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidCredentialsUseGenericUnauthorizedError() throws Exception {
        MvcResult sessionResult = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) sessionResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/session")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, read(sessionResult, "csrfToken"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"missing-account\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    void adminAndAuditorCanReadStableUserPagesButOrdinaryUserCannot() throws Exception {
        AccountUser alpha = directoryService.createUser(new SaveUserCommand(
                "alpha-user", "alpha@example.com", "Alpha", "13800000002"));
        directoryService.createUser(new SaveUserCommand(
                "beta-user", "beta@example.com", "Beta", "13800000003"));
        directoryService.createUser(new SaveUserCommand(
                "gamma-user", "gamma@example.com", "Gamma", "13800000004"));

        AccountUser auditor = directoryService.createUser(new SaveUserCommand(
                "auditor-user", "auditor@example.com", "审计员", "13800000005"));
        adminRoleMapper.insert(auditor.getId(), "ACCOUNT_AUDITOR");

        mockMvc.perform(get("/api/users?page=1&size=2&sort=account,asc")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.items[0].account").value("alpha-user"))
                .andExpect(jsonPath("$.items[0].password").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdBy").doesNotExist());

        mockMvc.perform(get("/api/users/" + alpha.getId())
                        .sessionAttr(AuthController.SESSION_USER_KEY,
                                new AccountSessionUser(auditor.getId(), auditor.getAccount(), auditor.getName())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("alpha-user"));

        mockMvc.perform(get("/api/users")
                        .sessionAttr(AuthController.SESSION_USER_KEY,
                                new AccountSessionUser("ordinary-user", "ordinary", "普通用户")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void userApiReturnsStableAuthenticationValidationAndNotFoundErrors() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());

        mockMvc.perform(get("/api/users?page=0")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.page").exists());

        mockMvc.perform(get("/api/users/not-found")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void adminCanCreateReplayUpdateAndDisableUserWithVersionedAudit() throws Exception {
        MvcResult sessionResult = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) sessionResult.getRequest().getSession(false);
        session.setAttribute(AuthController.SESSION_USER_KEY, adminSession());
        String csrfToken = read(sessionResult, "csrfToken");
        String createBody = "{\"account\":\"managed-user\",\"email\":\"managed@example.com\","
                + "\"name\":\"受管用户\",\"phone\":\"13800000006\"}";

        MvcResult created = mockMvc.perform(post("/api/users")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "create-managed-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
        String userId = read(created, "id");

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "create-managed-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/users/" + userId)
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "update-managed-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"managed-user\",\"email\":\"new@example.com\","
                                + "\"name\":\"新姓名\",\"phone\":\"13800000006\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.email").value("new@example.com"));

        mockMvc.perform(put("/api/users/" + userId)
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "stale-managed-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"managed-user\",\"email\":\"stale@example.com\","
                                + "\"name\":\"过期修改\",\"phone\":\"13800000006\",\"version\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));

        mockMvc.perform(put("/api/users/" + userId + "/status")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "disable-managed-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\",\"version\":2,\"reason\":\"测试停用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"))
                .andExpect(jsonPath("$.version").value(3));

        org.junit.jupiter.api.Assertions.assertEquals(
                3, auditLogService.search("admin-user", null, "USER", userId).size());
    }

    @Test
    void userWritesRequireCsrfIdempotencyAndAdminRole() throws Exception {
        MvcResult sessionResult = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) sessionResult.getRequest().getSession(false);
        String csrfToken = read(sessionResult, "csrfToken");
        String body = "{\"account\":\"guarded-user\",\"email\":\"guarded@example.com\","
                + "\"name\":\"受保护用户\",\"phone\":\"13800000007\"}";

        session.setAttribute(AuthController.SESSION_USER_KEY, adminSession());
        mockMvc.perform(post("/api/users")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors['Idempotency-Key']").exists());

        AccountUser auditor = directoryService.createUser(new SaveUserCommand(
                "write-auditor", "write-auditor@example.com", "只读审计员", "13800000008"));
        adminRoleMapper.insert(auditor.getId(), "ACCOUNT_AUDITOR");
        session.setAttribute(AuthController.SESSION_USER_KEY,
                new AccountSessionUser(auditor.getId(), auditor.getAccount(), auditor.getName()));

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "auditor-write")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void userCreateRejectsReusedIdempotencyKeyDuplicateAccountAndInvalidFields() throws Exception {
        MvcResult sessionResult = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) sessionResult.getRequest().getSession(false);
        session.setAttribute(AuthController.SESSION_USER_KEY, adminSession());
        String csrfToken = read(sessionResult, "csrfToken");
        String body = "{\"account\":\"unique-user\",\"email\":\"unique@example.com\","
                + "\"name\":\"唯一用户\",\"phone\":\"13800000009\"}";

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "reused-create-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "reused-create-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("唯一用户", "另一用户")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "duplicate-account-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "invalid-user-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"\",\"email\":\"invalid\",\"name\":\"\",\"phone\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.account").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void disablingCurrentOrLastEnabledAdminIsRejected() throws Exception {
        AccountUser targetAdmin = directoryService.createUser(new SaveUserCommand(
                "protected-admin", "protected@example.com", "受保护管理员", "13800000010"));
        adminRoleMapper.insert(targetAdmin.getId(), "ACCOUNT_ADMIN");

        MvcResult sessionResult = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) sessionResult.getRequest().getSession(false);
        String csrfToken = read(sessionResult, "csrfToken");

        session.setAttribute(AuthController.SESSION_USER_KEY,
                new AccountSessionUser(targetAdmin.getId(), targetAdmin.getAccount(), targetAdmin.getName()));
        mockMvc.perform(put("/api/users/" + targetAdmin.getId() + "/status")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "protect-current-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\",\"version\":1,\"reason\":\"测试保护\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENT_ADMIN_PROTECTED"));

        session.setAttribute(AuthController.SESSION_USER_KEY, adminSession());
        mockMvc.perform(put("/api/users/" + targetAdmin.getId() + "/status")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "protect-last-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\",\"version\":1,\"reason\":\"测试保护\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_PROTECTED"));
    }

    private String read(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get(field).asText();
    }

    private AccountSessionUser adminSession() {
        return new AccountSessionUser("admin-user", "admin", "管理员");
    }
}
