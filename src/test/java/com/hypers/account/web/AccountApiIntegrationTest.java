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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
        String applicationBody = "{\"appCode\":\"cms-ai\",\"name\":\"Carbon Service\",\"entryUrl\":\"http://localhost:9003\",\"ssoCallbackUrl\":\"http://localhost:9003/account-sso/callback\",\"permissionIframeUrl\":\"http://localhost:9003/account-admin/users/{externalUserId}/permissions\",\"notifyBaseUrl\":\"http://localhost:9003\",\"secret\":\"secret\",\"defaultTenantCode\":\"default\"}";

        mockMvc.perform(post("/api/applications")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appCode").value("cms-ai"))
                .andExpect(jsonPath("$.status").value("enabled"))
                .andExpect(jsonPath("$.secretVersion").value(1))
                .andExpect(jsonPath("$.secret").doesNotExist());

        mockMvc.perform(get("/api/applications/cms-ai")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carbon Service"))
                .andExpect(jsonPath("$.secret").doesNotExist());

        mockMvc.perform(get("/api/applications?keyword=cms")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appCode").value("cms-ai"))
                .andExpect(jsonPath("$[0].secret").doesNotExist());

        String updatedApplicationBody = applicationBody.replace("Carbon Service", "Carbon Service v2");
        mockMvc.perform(put("/api/applications/cms-ai")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedApplicationBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carbon Service v2"));

        mockMvc.perform(post("/api/applications/cms-ai/disable")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/applications/cms-ai")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));

        mockMvc.perform(post("/api/applications/cms-ai/enable")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isNoContent());

        MvcResult rotateResult = mockMvc.perform(post("/api/applications/cms-ai/secret/rotate")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newSecret").isNotEmpty())
                .andReturn();
        String newSecret = objectMapper.readTree(rotateResult.getResponse().getContentAsString()).get("newSecret").asText();
        assertThat(newSecret).isNotEqualTo("secret");

        MvcResult userResult = mockMvc.perform(post("/api/users")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"zhangsan\",\"email\":\"zhangsan@example.com\",\"name\":\"Zhang San\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("zhangsan"))
                .andExpect(jsonPath("$.status").value("enabled"))
                .andReturn();
        String userId = objectMapper.readTree(userResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/users/" + userId)
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("zhangsan@example.com"));

        mockMvc.perform(get("/api/users?keyword=zhang")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].account").value("zhangsan"));

        mockMvc.perform(put("/api/users/" + userId)
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"zhangsan\",\"email\":\"new@example.com\",\"name\":\"Zhang San Updated\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));

        mockMvc.perform(post("/api/users/{userId}/applications/{appCode}/authorize", userId, "cms-ai")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isNoContent());

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

        mockMvc.perform(post("/api/users/" + userId + "/disable")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + userId)
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));

        mockMvc.perform(post("/api/sso/tickets")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"appCode\":\"cms-ai\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/users/" + userId + "/enable")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/users/{userId}/applications/{appCode}/deauthorize", userId, "cms-ai")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isNoContent());

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
