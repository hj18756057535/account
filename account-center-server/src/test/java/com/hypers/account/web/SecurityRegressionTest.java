package com.hypers.account.web;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.RegisterApplicationCommand;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.security.HmacSignatureService;
import com.hypers.account.sso.AccountUserSnapshot;
import com.hypers.account.sso.SsoTicketService;
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
class SecurityRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountDirectoryService directoryService;

    @Autowired
    private SsoTicketService ticketService;

    @Autowired
    private HmacSignatureService signatureService;

    @Autowired
    private AdminRoleMapper adminRoleMapper;

    @BeforeEach
    void grantAdminRole() {
        adminRoleMapper.insert("admin-user", "ACCOUNT_ADMIN");
    }

    @Test
    void managementApiRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"anon\",\"email\":\"anon@example.com\",\"name\":\"匿名\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managementApiRejectsSessionWithoutAdminRole() throws Exception {
        mockMvc.perform(post("/api/users")
                        .sessionAttr(AuthController.SESSION_USER_KEY,
                                new AccountSessionUser("normal-user", "normal", "普通用户"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"normal\",\"email\":\"normal@example.com\",\"name\":\"普通用户\",\"phone\":\"13800000000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void applicationSecretIsWriteOnlyInApiResponses() throws Exception {
        ManagementSession management = managementSession();
        mockMvc.perform(post("/api/applications")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "secret-write-only")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody("secret-app", "http://localhost:9003/notify")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.application.appCode").value("secret-app"))
                .andExpect(jsonPath("$.application.secret").doesNotExist())
                .andExpect(jsonPath("$.secret").isNotEmpty());

        mockMvc.perform(get("/api/applications/secret-app").session(management.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    void applicationRegistrationRejectsUnsupportedUrlScheme() throws Exception {
        ManagementSession management = managementSession();
        mockMvc.perform(post("/api/applications")
                        .session(management.session())
                        .header(CsrfTokenManager.HEADER_NAME, management.csrfToken())
                        .header("Idempotency-Key", "bad-url-scheme")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationBody("bad-url-app", "file:///etc/passwd")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void ssoExchangeRejectsMissingSignatureHeaders() throws Exception {
        AccountApplication application = registerApp("signed-sso", "http://localhost:9003/callback");
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "signed-user",
                "signed@example.com",
                "签名用户",
                "13800000000"));
        directoryService.authorize(user.getId(), application.getAppCode());
        String code = ticketService.issue(application.getAppCode(), new AccountUserSnapshot(
                user.getId(),
                user.getAccount(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                application.getDefaultTenantCode()));

        mockMvc.perform(post("/openapi/sso/tickets/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appCode\":\"signed-sso\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ssoExchangeAcceptsValidSignatureHeaders() throws Exception {
        AccountApplication application = registerApp("signed-ok", "http://localhost:9003/callback");
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "signed-ok-user",
                "signed-ok@example.com",
                "签名成功用户",
                "13800000000"));
        directoryService.authorize(user.getId(), application.getAppCode());
        String code = ticketService.issue(application.getAppCode(), new AccountUserSnapshot(
                user.getId(),
                user.getAccount(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                application.getDefaultTenantCode()));
        String body = "{\"appCode\":\"signed-ok\",\"code\":\"" + code + "\"}";

        mockMvc.perform(post("/openapi/sso/tickets/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(signedHeaders("signed-ok", application.getSecret(), "/openapi/sso/tickets/exchange", body))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account").value("signed-ok-user"));
    }

    @Test
    void adminTicketCannotBeIssuedForUnauthorizedUserApplicationPair() throws Exception {
        registerApp("ticket-app", "http://localhost:9003/callback");
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "ticket-user",
                "ticket@example.com",
                "票据用户",
                "13800000000"));

        mockMvc.perform(post("/api/admin-tickets")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appCode\":\"ticket-app\",\"userId\":\"" + user.getId() + "\",\"purpose\":\"iframe-permission\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminTicketVerifyRejectsMissingSignatureHeaders() throws Exception {
        AccountApplication application = registerApp("ticket-signed", "http://localhost:9003/callback");
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "ticket-signed-user",
                "ticket-signed@example.com",
                "票据签名用户",
                "13800000000"));
        directoryService.authorize(user.getId(), application.getAppCode());
        String ticket = mockMvc.perform(post("/api/admin-tickets")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appCode\":\"ticket-signed\",\"userId\":\"" + user.getId() + "\",\"purpose\":\"iframe-permission\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replace("{\"code\":\"", "")
                .replace("\"}", "");

        mockMvc.perform(post("/openapi/admin-tickets/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appCode\":\"ticket-signed\",\"ticket\":\"" + ticket + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    private AccountApplication registerApp(String appCode, String callbackUrl) {
        return directoryService.registerApplication(new RegisterApplicationCommand(
                appCode,
                appCode,
                "http://localhost:9003",
                callbackUrl,
                "http://localhost:9003/permissions",
                "http://localhost:9003",
                "secret-" + appCode,
                "default"));
    }

    private AccountSessionUser adminSession() {
        return new AccountSessionUser("admin-user", "admin", "管理员");
    }

    private String applicationBody(String appCode, String notifyBaseUrl) {
        return "{\"appCode\":\"" + appCode + "\",\"name\":\"密钥应用\","
                + "\"entryUrl\":\"http://localhost:9003\","
                + "\"ssoCallbackUrl\":\"http://localhost:9003/callback\","
                + "\"permissionIframeUrl\":\"http://localhost:9003/permissions\","
                + "\"notifyBaseUrl\":\"" + notifyBaseUrl + "\","
                + "\"defaultTenantCode\":\"default\","
                + "\"protocolCapabilities\":[\"sso\"]}";
    }

    private ManagementSession managementSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/session")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        session.setAttribute(AuthController.SESSION_USER_KEY, adminSession());
        String csrfToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("csrfToken").asText();
        return new ManagementSession(session, csrfToken);
    }

    private record ManagementSession(MockHttpSession session, String csrfToken) {
    }

    private org.springframework.http.HttpHeaders signedHeaders(String appCode, String secret, String path, String body) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-" + System.nanoTime();
        String signText = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body;
        String signature = signatureService.sign(signText, secret);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Account-App-Code", appCode);
        headers.add("X-Account-Timestamp", timestamp);
        headers.add("X-Account-Nonce", nonce);
        headers.add("X-Account-Signature", signature);
        return headers;
    }
}
