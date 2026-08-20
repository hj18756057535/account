package com.hypers.account.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private String read(MvcResult result, String field) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get(field).asText();
    }

    private AccountSessionUser adminSession() {
        return new AccountSessionUser("admin-user", "admin", "管理员");
    }
}
