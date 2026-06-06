package com.hypers.account.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
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
        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("用户与授权")))
                .andExpect(content().string(containsString("应用准入授权")))
                .andExpect(content().string(containsString("iframe")));
    }

    @Test
    void applicationsPageContainsOnlyApplicationManagementWorkspace() throws Exception {
        mockMvc.perform(get("/applications"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("应用管理")))
                .andExpect(content().string(containsString("保存应用")))
                .andExpect(content().string(containsString("default")));
    }
}
