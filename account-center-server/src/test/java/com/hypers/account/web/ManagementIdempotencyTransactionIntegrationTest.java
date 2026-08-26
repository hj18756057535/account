package com.hypers.account.web;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountStore;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.auth.AdminAuthorizationService;
import com.hypers.account.mapper.IdempotencyRecordMapper;
import com.hypers.account.web.management.CsrfTokenManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ManagementIdempotencyTransactionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountStore accountStore;

    @MockitoBean
    private AdminAuthorizationService authorizationService;

    @MockitoSpyBean
    private IdempotencyRecordMapper idempotencyRecordMapper;

    @Test
    void idempotencyCompletionFailureRollsBackUserWrite() throws Exception {
        when(authorizationService.isAdmin("admin-user")).thenReturn(true);
        doReturn(0).when(idempotencyRecordMapper).complete(
                anyString(), anyInt(), anyString(), any(Instant.class));

        MvcResult sessionResult = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) sessionResult.getRequest().getSession(false);
        session.setAttribute(AuthController.SESSION_USER_KEY,
                new AccountSessionUser("admin-user", "admin", "管理员"));
        String csrfToken = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("csrfToken").asText();

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "rollback-incomplete-record")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"rollback-user\",\"email\":\"rollback@example.com\","
                                + "\"name\":\"回滚用户\",\"phone\":\"13800000011\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertNull(accountStore.findUserByAccount("rollback-user"));
    }
}
