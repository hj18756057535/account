package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.security.OpenApiSignatureVerifier;
import jakarta.servlet.RequestDispatcher;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ServerInternationalizationBoundaryTest {
    private final MockMvc mvc;
    private final ObjectMapper objectMapper;
    private final ApiMessages messages;

    @Test
    void legacyAuthenticationAndAuthorizationHaveLocalizedBodies() throws Exception {
        mvc.perform(get("/api/audit-logs").header("Accept-Language", "en-US"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Content-Language", "en-US"))
                .andExpect(jsonPath("$.error").value("Your session has expired. Please sign in again."));
        mvc.perform(get("/api/audit-logs").header("Accept-Language", "zh")
                        .sessionAttr(AuthController.SESSION_USER_KEY, new AccountSessionUser("no-role", "test", "测试")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("当前账号没有执行此操作的权限"));
    }

    @Test
    void malformedLegacyTicketRequestsRemainBadRequests() throws Exception {
        for (String body : new String[] {"", "null", "[]", "{broken"}) {
            mvc.perform(post("/api/admin-tickets").header("Accept-Language", "en")
                            .sessionAttr(AuthController.SESSION_USER_KEY, new AccountSessionUser("test", "test", "测试"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value(
                            "The ticket request is invalid or the user has no access to the application."));
        }
    }

    @Test
    void signatureErrorsAreLocalizedWithoutExposingValidationDetails() throws Exception {
        mvc.perform(post("/openapi/sso/tickets/exchange").header("Accept-Language", "en")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Content-Language", "en-US"))
                .andExpect(jsonPath("$.error").value("The request signature is invalid or has expired."));
        var verifier = mock(OpenApiSignatureVerifier.class);
        var application = mock(AccountApplication.class);
        when(application.getAppCode()).thenReturn("test");
        when(verifier.verify(any(), anyString(), anyString())).thenReturn(application);
        var filter = new OpenApiSignatureFilter(verifier, objectMapper, new LegacyApiErrorWriter(objectMapper, messages));
        for (String body : new String[] {"", "null", "[]", "{broken"}) {
            var request = new MockHttpServletRequest("POST", "/openapi/sso/tickets/exchange");
            request.addHeader("Accept-Language", "en");
            request.setContent(body.getBytes(StandardCharsets.UTF_8));
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
            assertThat(response.getContentAsString()).contains("The request signature is invalid or has expired.");
        }
    }

    @Test
    void serverRenderedLoginAndSsoPagesUseRequestLanguage() throws Exception {
        mvc.perform(get("/login").header("Accept-Language", "en"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("lang=\"en-US\"")))
                .andExpect(content().string(containsString("Sign in - Account Center")))
                .andExpect(content().string(containsString(">Password</label>")));
        mvc.perform(get("/sso/error").param("code", "callbackMismatch").header("Accept-Language", "en"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("The callback URL does not match.")));
        mvc.perform(get("/sso/error").param("message", "应用不存在").header("Accept-Language", "zh"))
                .andExpect(content().string(containsString("应用不存在")));
        mvc.perform(get("/sso/error").param("message", "untrusted-error-marker").header("Accept-Language", "en"))
                .andExpect(content().string(not(containsString("untrusted-error-marker"))))
                .andExpect(content().string(containsString("Authorization could not be completed.")));
    }

    @Test
    void loginFailuresAndFrameworkErrorsAreLocalized() throws Exception {
        mvc.perform(post("/login").param("account", "no-such-user").param("password", "synthetic")
                        .header("Accept-Language", "en"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", "Incorrect account or password."));
        mvc.perform(post("/login").param("account", "test").header("Accept-Language", "en"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("The request is invalid."));
        mvc.perform(put("/api/session").header("Accept-Language", "en"))
                .andExpect(status().isMethodNotAllowed()).andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.error").value("This request method is not supported for the resource."));
        mvc.perform(get("/missing-i18n-resource").header("Accept-Language", "en"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("The requested resource was not found."));
    }

    @Test
    void containerFallbackDoesNotExposeExceptionText() throws Exception {
        mvc.perform(get("/error").accept(MediaType.APPLICATION_JSON).header("Accept-Language", "en")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION, new IllegalStateException("private-error-marker"))
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "private-error-marker"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("The service is temporarily unavailable. Please retry later."))
                .andExpect(content().string(not(containsString("private-error-marker"))));
    }
}
