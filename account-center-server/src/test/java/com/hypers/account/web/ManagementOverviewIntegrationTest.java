package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.command.SaveUserCommand;
import com.hypers.account.app.service.UserManagementService;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.auth.AccountLoginService;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.web.management.CsrfTokenManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManagementOverviewIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AdminRoleMapper roles;
    @Autowired private UserManagementService userService;
    @Autowired private AccountLoginService login;
    @Autowired private AuditLogService audit;
    @Autowired private ObjectMapper json;

    @Test
    void aggregatesEmptyAndPopulatedDataAndLimitsFailureEvidence() throws Exception {
        var session = managementSession("ACCOUNT_AUDITOR");
        mvc.perform(get("/api/overview").session(session))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.counts.users").value(0))
                .andExpect(jsonPath("$.insights").isEmpty())
                .andExpect(jsonPath("$.downstreamStatus").value("not_checked"));
        var user = userService.createManagedUser(new SaveUserCommand("overview-user", "test@example.com", "Test", "123"), null);
        jdbc.update("update account_users set status='disabled', updated_at=? where id=?",
                java.sql.Timestamp.from(java.time.Instant.now().minus(31, java.time.temporal.ChronoUnit.DAYS)), user.getId());
        jdbc.update("insert into account_applications (app_code, name, secret, status, entry_url, sso_callback_url, permission_iframe_url, notify_base_url) values ('overview-app','Test','synthetic-secret','enabled','https://example.com','','','')");
        jdbc.update("insert into account_user_applications (user_id, app_code, desired_status) values (?, 'overview-app', 'enabled')", user.getId());
        for (int i = 0; i < 12; i++) audit.securityEvent(null, "MANAGEMENT_LOGIN_FAILED", false);
        jdbc.update("insert into account_operation_logs (id, operation_type, target_type, target_id, outcome, created_at) values ('old-failure', 'OLD', 'MANAGEMENT_SESSION', 'management', 'failure', '2020-01-01 00:00:00')");
        var result = mvc.perform(get("/api/overview").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.users").value(1))
                .andExpect(jsonPath("$.counts.disabledUsers").value(1))
                .andExpect(jsonPath("$.counts.grantedAccess").value(1))
                .andExpect(jsonPath("$.counts.longDisabledUsers").value(1))
                .andExpect(jsonPath("$.recentFailures").value(12))
                .andExpect(jsonPath("$.failureEvents.length()").value(10))
                .andExpect(jsonPath("$.insights.length()").value(2)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("synthetic-secret", "test@example.com");
    }

    @Test
    void deniesAnonymousAndOrdinaryUsersAndRecordsTraceWithoutRequestData() throws Exception {
        var response = mvc.perform(get("/api/overview").param("secret", "never-audit-this"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();
        var trace = response.getHeader("X-Trace-Id");
        assertThat(jdbc.queryForObject("select count(*) from account_operation_logs where trace_id=? and outcome='failure'", Integer.class, trace)).isEqualTo(1);
        var ordinary = new MockHttpSession();
        ordinary.setAttribute(AuthController.SESSION_USER_KEY, new AccountSessionUser("ordinary", "ordinary", "Test"));
        mvc.perform(get("/api/overview").session(ordinary)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("select detail from account_operation_logs").toString()).doesNotContain("never-audit-this");
    }

    @Test
    void loginSuccessFailureAndCsrfDenialAreAuditedWithoutCredentials() throws Exception {
        var user = userService.createManagedUser(new SaveUserCommand("login-overview", "test@example.com", "Test", "123"), null);
        userService.updatePassword(user.getId(), login.encodePassword("synthetic-password"));
        roles.insert(user.getId(), "ACCOUNT_ADMIN");
        var initial = mvc.perform(get("/api/session")).andReturn();
        var session = (MockHttpSession) initial.getRequest().getSession(false);
        var csrf = json.readTree(initial.getResponse().getContentAsString()).get("csrfToken").asText();
        mvc.perform(post("/api/session").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"untrusted-input\",\"password\":\"private-password\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/session").session(session).header(CsrfTokenManager.HEADER_NAME, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"untrusted-input\",\"password\":\"private-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/session").session(session).header(CsrfTokenManager.HEADER_NAME, csrf)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"account\":\"login-overview\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isOk());
        var events = jdbc.queryForList("select operation_type, outcome, trace_id, operator_id, target_id, detail from account_operation_logs");
        assertThat(events.toString()).contains("MANAGEMENT_LOGIN_SUCCEEDED", "MANAGEMENT_LOGIN_FAILED", "MANAGEMENT_ACCESS_DENIED")
                .doesNotContain("untrusted-input", "private-password", "synthetic-password");
    }

    private MockHttpSession managementSession(String role) {
        roles.insert("overview-reader", role);
        var session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_KEY, new AccountSessionUser("overview-reader", "reader", "Reader"));
        return session;
    }
}
