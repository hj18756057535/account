package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.web.management.CsrfTokenManager;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class ApiInternationalizationTest {

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final AdminRoleMapper roles;
    private final ApiMessages messages;

    @Test
    void languageNegotiationPreservesAuthenticationCodesAndDoesNotLeakAcrossRequests() throws Exception {
        mockMvc.perform(get("/api/audit-events").header("Accept-Language", "fr-FR,en-GB;q=0.8,zh;q=0.5"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Language", "en-US"))
                .andExpect(header().string("Vary", "Accept-Language"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Your session has expired. Please sign in again."));
        for (String language : new String[] {"fr-FR", "en;q=0,zh-CN;q=1", "invalid;header", ""}) {
            mockMvc.perform(get("/api/audit-events").header("Accept-Language", language))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Content-Language", "zh-CN"))
                    .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                    .andExpect(jsonPath("$.message").value("登录状态已失效，请重新登录"));
        }
        mockMvc.perform(get("/api/audit-events"))
                .andExpect(header().string("Content-Language", "zh-CN"));
    }

    @Test
    void loginValidationAndCsrfUseTheRequestedLanguage() throws Exception {
        var initialized = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) initialized.getRequest().getSession(false);
        String csrf = objectMapper.readTree(initialized.getResponse().getContentAsString()).get("csrfToken").asText();
        mockMvc.perform(post("/api/session").session(session).header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andExpect(jsonPath("$.message").value("Request verification has expired. Refresh the page and retry."));
        mockMvc.perform(post("/api/session").session(session).header("Accept-Language", "en-US")
                        .header(CsrfTokenManager.HEADER_NAME, csrf)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Request field validation failed."))
                .andExpect(jsonPath("$.fieldErrors.account").value("Enter your account."))
                .andExpect(jsonPath("$.fieldErrors.password").value("Enter your password."));
        mockMvc.perform(post("/api/session").session(session).header("Accept-Language", "en-US")
                        .header(CsrfTokenManager.HEADER_NAME, csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"unknown-i18n-user\",\"password\":\"synthetic-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Incorrect account or password."));
    }

    @Test
    void explicitValidationNotFoundAndRoleDenialAreLocalized() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_KEY, new AccountSessionUser("i18n-admin", "admin", "测试"));
        mockMvc.perform(get("/api/users").session(session).header("Accept-Language", "en"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You do not have permission to perform this operation."));
        roles.insert("i18n-admin", "ACCOUNT_ADMIN");
        mockMvc.perform(get("/api/users").session(session).param("size", "101").header("Accept-Language", "en"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors.size").value("Page size must be between 1 and 100."));
        mockMvc.perform(get("/api/audit-events/missing-i18n-event").session(session).header("Accept-Language", "en"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_EVENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Audit event not found."));
    }

    @Test
    void catalogsHaveMatchingKeysAndFallbackDoesNotExposeInternalMessages() throws Exception {
        Properties chinese = catalog("i18n/api.properties");
        Properties english = catalog("i18n/api_en.properties");
        assertThat(chinese.stringPropertyNames()).isEqualTo(english.stringPropertyNames());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en");
        for (String key : chinese.stringPropertyNames()) {
            assertThat(messages.text(request, key)).isEqualTo(english.getProperty(key)).isNotBlank();
        }
        assertThat(messages.text(request, "synthetic-private-error")).isEqualTo("The request is invalid.");
    }

    @Test
    void changingLanguageDoesNotChangeWriteIdempotencyOrBusinessData() throws Exception {
        var initialized = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) initialized.getRequest().getSession(false);
        String csrf = objectMapper.readTree(initialized.getResponse().getContentAsString()).get("csrfToken").asText();
        roles.insert("i18n-writer", "ACCOUNT_ADMIN");
        session.setAttribute(AuthController.SESSION_USER_KEY, new AccountSessionUser("i18n-writer", "admin", "管理员"));
        String body = "{\"account\":\"i18n-user\",\"name\":\"保留中文姓名\","
                + "\"email\":\"i18n@example.test\",\"phone\":\"13800000000\"}";
        String firstId = null;
        for (String language : new String[] {"zh-CN", "en-US"}) {
            var response = mockMvc.perform(post("/api/users").session(session)
                            .header("Accept-Language", language).header(CsrfTokenManager.HEADER_NAME, csrf)
                            .header("Idempotency-Key", "i18n-write").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Content-Language", language))
                    .andExpect(jsonPath("$.name").value("保留中文姓名")).andReturn();
            String id = objectMapper.readTree(response.getResponse().getContentAsString()).get("id").asText();
            if (firstId == null) firstId = id;
            else assertThat(id).isEqualTo(firstId);
        }
        mockMvc.perform(post("/api/users").session(session).header("Accept-Language", "en")
                        .header(CsrfTokenManager.HEADER_NAME, csrf).header("Idempotency-Key", "i18n-duplicate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("This account already exists."));
    }

    private Properties catalog(String path) throws Exception {
        Properties result = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            result.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return result;
    }
}
