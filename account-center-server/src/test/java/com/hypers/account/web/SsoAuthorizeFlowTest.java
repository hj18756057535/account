package com.hypers.account.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountStore;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.RegisterApplicationCommand;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.auth.AccountLoginService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * SSO 浏览器流程集成测试。
 * 验证：未登录跳登录、已登录已授权回调应用、已登录未授权显示错误。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SsoAuthorizeFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountDirectoryService directoryService;

    @Autowired
    private AccountLoginService loginService;

    @Autowired
    private AccountStore store;

    @Test
    void unauthenticatedUserRedirectsToLoginPage() throws Exception {
        mockMvc.perform(get("/sso/authorize")
                        .param("appCode", "cms-ai")
                        .param("redirectUri", "http://localhost:9003/callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    assert url != null && url.startsWith("/login");
                });
    }

    @Test
    void authenticatedAndAuthorizedUserGetsRedirectWithCode() throws Exception {
        // 创建应用和用户，设置密码，授权
        directoryService.registerApplication(new RegisterApplicationCommand(
                "cms-ai", "双碳服务", "http://localhost:9003",
                "http://localhost:9003/callback",
                "http://localhost:9003/permissions",
                "http://localhost:9003", "secret", "default"));
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "testuser", "test@example.com", "测试", "13800000000"));
        // 通过 store 设置密码
        String encoded = loginService.encodePassword("password123");
        store.setUserPassword(user.getId(), encoded);
        directoryService.authorize(user.getId(), "cms-ai");

        // 登录并保持 session
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/login")
                        .param("account", "testuser")
                        .param("password", "password123")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        // SSO 授权应返回 302 重定向到应用 callback（带 code 参数）
        mockMvc.perform(get("/sso/authorize")
                        .param("appCode", "cms-ai")
                        .param("redirectUri", "http://localhost:9003/callback")
                        .param("state", "random123")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    assert url != null && url.startsWith("http://localhost:9003/callback?code=");
                    assert url.contains("state=random123");
                });
    }

    @Test
    void authenticatedButUnauthorizedUserSeesErrorPage() throws Exception {
        // 创建应用和用户，但不授权
        directoryService.registerApplication(new RegisterApplicationCommand(
                "cms-ai", "双碳服务", "http://localhost:9003",
                "http://localhost:9003/callback",
                "http://localhost:9003/permissions",
                "http://localhost:9003", "secret", "default"));
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "noperm", "noperm@example.com", "无权限", "13800000000"));
        String encoded = loginService.encodePassword("password123");
        store.setUserPassword(user.getId(), encoded);

        // 登录并保持 session
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/login")
                        .param("account", "noperm")
                        .param("password", "password123")
                        .session(session))
                .andExpect(status().is3xxRedirection());

        // SSO 授权应跳转到错误页
        mockMvc.perform(get("/sso/authorize")
                        .param("appCode", "cms-ai")
                        .param("redirectUri", "http://localhost:9003/callback")
                        .session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void loginPageRenders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void loginWithWrongPasswordRedirectsWithError() throws Exception {
        directoryService.registerApplication(new RegisterApplicationCommand(
                "cms-ai", "双碳服务", "http://localhost:9003",
                "http://localhost:9003/callback",
                "http://localhost:9003/permissions",
                "http://localhost:9003", "secret", "default"));
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "testuser", "test@example.com", "测试", "13800000000"));
        String encoded = loginService.encodePassword("correct");
        store.setUserPassword(user.getId(), encoded);

        // 错误密码应重定向回登录页
        mockMvc.perform(post("/login")
                        .param("account", "testuser")
                        .param("password", "wrong"))
                .andExpect(status().is3xxRedirection());
    }
}
