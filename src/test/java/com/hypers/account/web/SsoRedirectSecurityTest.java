package com.hypers.account.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.RegisterApplicationCommand;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.auth.AccountSessionUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SsoRedirectSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountDirectoryService directoryService;

    @Test
    void ssoAuthorizeRejectsUnregisteredRedirectUri() throws Exception {
        AccountApplication application = directoryService.registerApplication(new RegisterApplicationCommand(
                "redirect-safe",
                "回调校验应用",
                "http://localhost:9003",
                "http://localhost:9003/callback",
                "http://localhost:9003/permissions",
                "http://localhost:9003",
                "secret",
                "default"));
        AccountUser user = directoryService.createUser(new SaveUserCommand(
                "redirect-safe-user",
                "redirect-safe@example.com",
                "回调校验用户",
                "13800000000"));
        directoryService.authorize(user.getId(), application.getAppCode());

        mockMvc.perform(get("/sso/authorize")
                        .sessionAttr(AuthController.SESSION_USER_KEY, new AccountSessionUser(user.getId(), user.getAccount(), user.getName()))
                        .param("appCode", application.getAppCode())
                        .param("redirectUri", "https://evil.example/callback"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String url = result.getResponse().getRedirectedUrl();
                    assert url != null;
                    assert !url.startsWith("https://evil.example");
                    assert url.startsWith("/sso/error");
                });
    }
}
