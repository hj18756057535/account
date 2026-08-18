package com.hypers.account.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class HomePageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePageLinksToUserAuthorizationAndApplicationManagementPages() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Account Center")))
                .andExpect(content().string(containsString("/users")))
                .andExpect(content().string(containsString("/applications")));
    }

    @Test
    void usersPageKeepsUserAndAuthorizationWorkflowTogether() throws Exception {
        mockMvc.perform(get("/users")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/users")))
                .andExpect(content().string(containsString("iframe")));
    }

    @Test
    void applicationsPageContainsApplicationListAndManagement() throws Exception {
        mockMvc.perform(get("/applications")
                        .sessionAttr(AuthController.SESSION_USER_KEY, adminSession()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/applications")));
    }

    private AccountSessionUser adminSession() {
        return new AccountSessionUser("admin-user", "admin", "admin");
    }
}