package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.security.HmacSignatureService;
import com.hypers.account.web.management.CsrfTokenManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HmacSignatureService signatureService;

    @Autowired
    private AdminRoleMapper adminRoleMapper;

    @BeforeEach
    void grantAdminRole() {
        adminRoleMapper.insert("admin-user", "ACCOUNT_ADMIN");
    }

    @Test
    void fullUserAndApplicationCrudFlow() throws Exception {
        MvcResult sessionResult = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession managementSession =
                (MockHttpSession) sessionResult.getRequest().getSession(false);
        managementSession.setAttribute(AuthController.SESSION_USER_KEY, adminSession());
        String csrfToken = objectMapper.readTree(sessionResult.getResponse().getContentAsString())
                .get("csrfToken").asText();
        String applicationBody = "{\"appCode\":\"cms-ai\",\"name\":\"Carbon Service\","
                + "\"entryUrl\":\"http://localhost:9003\","
                + "\"ssoCallbackUrl\":\"http://localhost:9003/account-sso/callback\","
                + "\"permissionIframeUrl\":\"http://localhost:9003/permissions\","
                + "\"notifyBaseUrl\":\"http://localhost:9003/notify\","
                + "\"defaultTenantCode\":\"default\","
                + "\"protocolCapabilities\":[\"sso\",\"admin_ticket\",\"user_sync\"]}";

        MvcResult applicationResult = mockMvc.perform(post("/api/applications")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-create-app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.appCode").value("cms-ai"))
                .andExpect(jsonPath("$.application.status").value("enabled"))
                .andExpect(jsonPath("$.application.secretVersion").value(1))
                .andExpect(jsonPath("$.application.secret").doesNotExist())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andReturn();
        String newSecret = objectMapper.readTree(applicationResult.getResponse().getContentAsString())
                .get("secret").asText();

        mockMvc.perform(get("/api/applications/cms-ai")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carbon Service"))
                .andExpect(jsonPath("$.secret").doesNotExist());

        mockMvc.perform(get("/api/applications?query=cms")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appCode").value("cms-ai"))
                .andExpect(jsonPath("$[0].secret").doesNotExist());

        String updatedApplicationBody = "{\"name\":\"Carbon Service v2\","
                + "\"entryUrl\":\"http://localhost:9003\","
                + "\"ssoCallbackUrl\":\"http://localhost:9003/account-sso/callback\","
                + "\"permissionIframeUrl\":\"http://localhost:9003/permissions\","
                + "\"notifyBaseUrl\":\"http://localhost:9003/notify\","
                + "\"defaultTenantCode\":\"default\","
                + "\"protocolCapabilities\":[\"sso\",\"admin_ticket\",\"user_sync\"],"
                + "\"version\":1}";
        mockMvc.perform(put("/api/applications/cms-ai")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-update-app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedApplicationBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carbon Service v2"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(put("/api/applications/cms-ai/status")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-disable-app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\",\"version\":2,\"reason\":\"全流程停用\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/applications/cms-ai")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));

        mockMvc.perform(put("/api/applications/cms-ai/status")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-enable-app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"enabled\",\"version\":3,\"reason\":\"全流程恢复\"}"))
                .andExpect(status().isOk());

        MvcResult rotateResult = mockMvc.perform(post("/api/applications/cms-ai/secret/rotate")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-rotate-app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":4,\"reason\":\"全流程轮换\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andReturn();
        String rotatedSecret = objectMapper.readTree(rotateResult.getResponse().getContentAsString())
                .get("secret").asText();
        assertThat(rotatedSecret).isNotEqualTo(newSecret);
        newSecret = rotatedSecret;

        MvcResult userResult = mockMvc.perform(post("/api/users")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-create-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"zhangsan\",\"email\":\"zhangsan@example.com\",\"name\":\"Zhang San\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.account").value("zhangsan"))
                .andExpect(jsonPath("$.status").value("enabled"))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn();
        String userId = objectMapper.readTree(userResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/users/" + userId)
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("zhangsan@example.com"));

        mockMvc.perform(get("/api/users?query=zhang")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].account").value("zhangsan"));

        mockMvc.perform(put("/api/users/" + userId)
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-update-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"zhangsan\",\"email\":\"new@example.com\",\"name\":\"Zhang San Updated\",\"phone\":\"13800000000\",\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(put("/api/users/{userId}/application-access/{appCode}", userId, "cms-ai")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-enable-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"enabled\",\"version\":0,\"reason\":\"全流程开通\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/users/" + userId + "/applications")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appCode").value("cms-ai"));

        MvcResult ticketResult = mockMvc.perform(post("/api/sso/tickets")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"appCode\":\"cms-ai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andReturn();
        String code = objectMapper.readTree(ticketResult.getResponse().getContentAsString()).get("code").asText();

        String exchangeBody = "{\"appCode\":\"cms-ai\",\"code\":\"" + code + "\"}";
        MvcResult exchangeResult = mockMvc.perform(post("/openapi/sso/tickets/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(signedHeaders("cms-ai", newSecret, "/openapi/sso/tickets/exchange", exchangeBody))
                        .content(exchangeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("zhangsan"))
                .andExpect(jsonPath("$.tenantCode").value("default"))
                .andReturn();
        assertThat(exchangeResult.getResponse().getContentAsString()).contains("new@example.com");

        mockMvc.perform(put("/api/users/" + userId + "/status")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-disable-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\",\"version\":2,\"reason\":\"全流程测试停用\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));

        mockMvc.perform(get("/api/users/" + userId)
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));

        mockMvc.perform(post("/api/sso/tickets")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"appCode\":\"cms-ai\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/users/" + userId + "/status")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-enable-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"enabled\",\"version\":3,\"reason\":\"全流程测试恢复\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));

        mockMvc.perform(put("/api/users/{userId}/application-access/{appCode}", userId, "cms-ai")
                        .session(managementSession)
                        .header(CsrfTokenManager.HEADER_NAME, csrfToken)
                        .header("Idempotency-Key", "full-flow-disable-access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"disabled\",\"version\":1,\"reason\":\"全流程关闭\"}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/users/" + userId + "/applications")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private AccountSessionUser adminSession() {
        return new AccountSessionUser("admin-user", "admin", "admin");
    }

    private HttpHeaders signedHeaders(String appCode, String secret, String path, String body) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-" + System.nanoTime();
        String signText = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body;
        String signature = signatureService.sign(signText, secret);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Account-App-Code", appCode);
        headers.add("X-Account-Timestamp", timestamp);
        headers.add("X-Account-Nonce", nonce);
        headers.add("X-Account-Signature", signature);
        return headers;
    }
}
