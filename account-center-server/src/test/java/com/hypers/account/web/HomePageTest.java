package com.hypers.account.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.AdminRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomePageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminRoleMapper adminRoleMapper;

    @BeforeEach
    void grantAdminRole() {
        adminRoleMapper.insert("admin-user", "ACCOUNT_ADMIN");
    }

    @Test
    void homePageLinksToUserAuthorizationAndApplicationManagementPages() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Account Center")))
                .andExpect(content().string(containsString("/console/users")))
                .andExpect(content().string(containsString("/console/applications")));
    }

    @Test
    void applicationsPageRedirectsToConsole() throws Exception {
        mockMvc.perform(get("/applications")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/console/applications"));
    }

    private AccountSessionUser adminSession() {
        return new AccountSessionUser("admin-user", "admin", "admin");
    }
}
