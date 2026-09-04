package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.web.management.CsrfTokenManager;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
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
class ManagementAuditIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AdminRoleMapper adminRoleMapper;
    @Autowired private AuditLogService auditLogService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void paginationIsStableAndHistoricalSensitiveDetailIsNotReturned() throws Exception {
        MockHttpSession session = session("ACCOUNT_ADMIN");
        for (String id : new String[] {"audit-a", "audit-b"}) {
            jdbcTemplate.update("insert into account_operation_logs "
                            + "(id, operator_id, operation_type, target_type, target_id, detail, created_at) "
                            + "values (?, 'audit-operator', 'VERIFY', 'SSO_TICKET', ?, ?, '2026-09-04 00:00:00')",
                    id, "synthetic-ticket-value", "synthetic-private-detail");
        }
        mockMvc.perform(get("/api/audit-events").session(session)
                        .param("operatorId", "audit-operator").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].id").value("audit-b"))
                .andExpect(jsonPath("$.items[0].outcome").value("unknown"))
                .andExpect(jsonPath("$.items[0].detail").doesNotExist())
                .andExpect(content().string(not(containsString("synthetic-private-detail"))))
                .andExpect(content().string(not(containsString("synthetic-ticket-value"))));
        mockMvc.perform(get("/api/audit-events").session(session)
                        .param("operatorId", "audit-operator").param("size", "1").param("page", "2"))
                .andExpect(jsonPath("$.items[0].id").value("audit-a"));
        mockMvc.perform(get("/api/audit-events/audit-b").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("audit-b"))
                .andExpect(jsonPath("$.targetId").isEmpty())
                .andExpect(jsonPath("$.traceId").isEmpty())
                .andExpect(jsonPath("$.detail").doesNotExist());
        mockMvc.perform(get("/api/audit-events/missing").session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/audit-events").session(session).param("page", "0"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/audit-events").session(session).param("size", "101"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/audit-events").session(session).param("traceId", "x".repeat(65)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void auditEndpointsRequireManagementRoleAndAuditorIsReadOnly() throws Exception {
        mockMvc.perform(get("/api/audit-events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(get("/api/audit-events/missing"))
                .andExpect(status().isUnauthorized());
        MockHttpSession ordinary = new MockHttpSession();
        ordinary.setAttribute(AuthController.SESSION_USER_KEY,
                new AccountSessionUser("ordinary", "ordinary", "普通用户"));
        mockMvc.perform(get("/api/audit-events").session(ordinary))
                .andExpect(status().isForbidden());
        MockHttpSession auditor = session("ACCOUNT_AUDITOR");
        mockMvc.perform(get("/api/audit-events").session(auditor).param("operatorId", "not-found"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(post("/api/audit-events").session(auditor))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void managementWriteAuditHasResponseTraceAndThreadContextIsCleared() throws Exception {
        MvcResult initialized = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) initialized.getRequest().getSession(false);
        adminRoleMapper.insert("audit-admin", "ACCOUNT_ADMIN");
        session.setAttribute(AuthController.SESSION_USER_KEY,
                new AccountSessionUser("audit-admin", "admin", "管理员"));
        String csrf = objectMapper.readTree(initialized.getResponse().getContentAsString()).get("csrfToken").asText();
        MvcResult created = mockMvc.perform(post("/api/users").session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrf)
                        .header("Idempotency-Key", "audit-create-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"trace-user\",\"name\":\"测试\","
                                + "\"email\":\"trace@example.test\",\"phone\":\"13800000001\"}"))
                .andExpect(status().isCreated()).andReturn();
        String trace = created.getResponse().getHeader("X-Trace-Id");
        mockMvc.perform(get("/api/audit-events").session(session).param("traceId", trace))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].operationType").value("USER_CREATED"))
                .andExpect(jsonPath("$.items[0].outcome").value("success"))
                .andExpect(jsonPath("$.items[0].traceId").value(trace));
        assertThat(MDC.get(AuditLogService.TRACE_CONTEXT_KEY)).isNull();
        auditLogService.log("outside-request", "CHECK", "USER", "synthetic-user", "{}");
        assertThat(auditLogService.search("outside-request", null, null, null).getFirst().getTraceId()).isNull();
    }

    private MockHttpSession session(String role) {
        String userId = "audit-" + role;
        adminRoleMapper.insert(userId, role);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_KEY, new AccountSessionUser(userId, "audit", "审计"));
        return session;
    }
}
