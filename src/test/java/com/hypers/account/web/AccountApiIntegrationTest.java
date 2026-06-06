package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AccountApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void accountApiSupportsApplicationAuthorizationAndTicketExchange() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appCode\":\"cms-ai\",\"name\":\"双碳服务\",\"entryUrl\":\"http://localhost:9003\",\"ssoCallbackUrl\":\"http://localhost:9003/account-sso/callback\",\"permissionIframeUrl\":\"http://localhost:9003/account-admin/users/{externalUserId}/permissions\",\"notifyBaseUrl\":\"http://localhost:9003\",\"secret\":\"secret\",\"defaultTenantCode\":\"default\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appCode").value("cms-ai"));

        MvcResult userResult = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"zhangsan\",\"email\":\"zhangsan@example.com\",\"name\":\"张三\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("zhangsan"))
                .andReturn();
        JsonNode userJson = objectMapper.readTree(userResult.getResponse().getContentAsString());
        String userId = userJson.get("id").asText();

        mockMvc.perform(post("/api/users/{userId}/applications/{appCode}/authorize", userId, "cms-ai"))
                .andExpect(status().isNoContent());

        MvcResult ticketResult = mockMvc.perform(post("/api/sso/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + userId + "\",\"appCode\":\"cms-ai\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").isNotEmpty())
                .andReturn();
        String code = objectMapper.readTree(ticketResult.getResponse().getContentAsString()).get("code").asText();

        MvcResult exchangeResult = mockMvc.perform(post("/openapi/sso/tickets/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appCode\":\"cms-ai\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("zhangsan"))
                .andExpect(jsonPath("$.tenantCode").value("default"))
                .andReturn();

        assertThat(exchangeResult.getResponse().getContentAsString()).contains("zhangsan@example.com");
    }
}
