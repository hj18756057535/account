package com.hypers.account.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void homePageRedirectsToConsole() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/console/users"));
    }

    @Test
    void auditPageRedirectsToConsole() throws Exception {
        mockMvc.perform(get("/audit-logs")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/console/audit-events"));
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
