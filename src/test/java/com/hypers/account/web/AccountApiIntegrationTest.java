package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    void fullUserAndApplicationCrudFlow() throws Exception {
        // Create application
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appCode\":\"cms-ai\",\"name\":\"双碳服务\",\"entryUrl\":\"http://localhost:9003\",\"ssoCallbackUrl\":\"http://localhost:9003/account-sso/callback\",\"permissionIframeUrl\":\"http://localhost:9003/account-admin/users/{externalUserId}/permissions\",\"notifyBaseUrl\":\"http://localhost:9003\",\"secret\":\"secret\",\"defaultTenantCode\":\"default\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appCode").value("cms-ai"))
                .andExpect(jsonPath("$.status").value("enabled"))
                .andExpect(jsonPath("$.secretVersion").value(1));

        // Get application
        mockMvc.perform(get("/api/applications/cms-ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("双碳服务"));

        // Search applications
        mockMvc.perform(get("/api/applications?keyword=cms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appCode").value("cms-ai"));

        // Update application
        mockMvc.perform(put("/api/applications/cms-ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appCode\":\"cms-ai\",\"name\":\"双碳服务v2\",\"entryUrl\":\"http://localhost:9003\",\"ssoCallbackUrl\":\"http://localhost:9003/account-sso/callback\",\"permissionIframeUrl\":\"http://localhost:9003/account-admin/users/{externalUserId}/permissions\",\"notifyBaseUrl\":\"http://localhost:9003\",\"secret\":\"secret\",\"defaultTenantCode\":\"default\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("双碳服务v2"));

        // Disable application
        mockMvc.perform(post("/api/applications/cms-ai/disable"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/applications/cms-ai"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));

        // Enable application
        mockMvc.perform(post("/api/applications/cms-ai/enable"))
                .andExpect(status().isNoContent());

        // Rotate secret
        MvcResult rotateResult = mockMvc.perform(post("/api/applications/cms-ai/secret/rotate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newSecret").isNotEmpty())
                .andReturn();
        String newSecret = objectMapper.readTree(rotateResult.getResponse().getContentAsString()).get("newSecret").asText();
        assertThat(newSecret).isNotEqualTo("secret");

        // Create user
        MvcResult userResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"zhangsan\",\"email\":\"zhangsan@example.com\",\"name\":\"张三\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("zhangsan"))
                .andExpect(jsonPath("$.status").value("enabled"))
                .andReturn();
        String userId = objectMapper.readTree(userResult.getResponse().getContentAsString()).get("id").asText();

        // Get user
        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("zhangsan@example.com"));

        // Search users
        mockMvc.perform(get("/api/users?keyword=zhang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].account").value("zhangsan"));

        // Update user
        mockMvc.perform(put("/api/users/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"zhangsan\",\"email\":\"new@example.com\",\"name\":\"张三三\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"));

        // Authorize user
        mockMvc.perform(post("/api/users/{userId}/applications/{appCode}/authorize", userId, "cms-ai"))
                .andExpect(status().isNoContent());

        // Get user's authorized applications
        mockMvc.perform(get("/api/users/" + userId + "/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].appCode").value("cms-ai"));

        // Issue SSO ticket
        MvcResult ticketResult = mockMvc.perform(post("/api/sso/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"appCode\":\"cms-ai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andReturn();
        String code = objectMapper.readTree(ticketResult.getResponse().getContentAsString()).get("code").asText();

        // Exchange ticket
        MvcResult exchangeResult = mockMvc.perform(post("/openapi/sso/tickets/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appCode\":\"cms-ai\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("zhangsan"))
                .andExpect(jsonPath("$.tenantCode").value("default"))
                .andReturn();

        assertThat(exchangeResult.getResponse().getContentAsString()).contains("new@example.com");

        // Disable user
        mockMvc.perform(post("/api/users/" + userId + "/disable"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("disabled"));

        // Disabled user cannot get SSO ticket
        mockMvc.perform(post("/api/sso/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"appCode\":\"cms-ai\"}"))
                .andExpect(status().isBadRequest());

        // Enable user
        mockMvc.perform(post("/api/users/" + userId + "/enable"))
                .andExpect(status().isNoContent());

        // Deauthorize
        mockMvc.perform(post("/api/users/{userId}/applications/{appCode}/deauthorize", userId, "cms-ai"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + userId + "/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }
}
