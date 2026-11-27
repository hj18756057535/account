package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.command.SaveApplicationCommand;
import com.hypers.account.app.command.SaveUserCommand;
import com.hypers.account.app.model.AccountUser;
import com.hypers.account.app.service.ApplicationManagementService;
import com.hypers.account.app.service.UserManagementService;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.AccountUserApplicationMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 验证应用切换只暴露当前会话已准入的导航字段，普通用户无需管理权限。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserApplicationsIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserManagementService users;
    @Autowired
    private ApplicationManagementService applications;
    @Autowired
    private AccountUserApplicationMapper grants;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private SqlSessionTemplate sqlSession;

    private AccountUser user;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        user = users.createUser(new SaveUserCommand("switch-user", "switch@example.test", "Switcher", ""));
        session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_KEY,
                new AccountSessionUser(user.getId(), user.getAccount(), user.getName()));
    }

    @Test
    void unauthenticatedRequestsReturnAnApiError() throws Exception {
        mvc.perform(get("/api/session/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void ordinaryUserOnlySeesOwnAvailableEntriesAndNoCredentials() throws Exception {
        create("my-app", "", true);
        create("other-app", "", false);
        AccountUser other = users.createUser(new SaveUserCommand("another-switcher", "other@example.test", "Other", ""));
        grants.insert(other.getId(), "other-app");
        create("revoked-app", "", true);
        jdbc.update("update account_applications set secret_state = 'revoked' where app_code = 'revoked-app'");
        create("empty-app", "", true);
        jdbc.update("update account_applications set entry_url = '' where app_code = 'empty-app'");

        var response = mvc.perform(get("/api/session/applications").session(session).param("userId", other.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].appCode").value("my-app"))
                .andExpect(jsonPath("$[0].entryUrl").value("https://my-app.example.test/login"))
                .andReturn();
        assertThat(json.readTree(response.getResponse().getContentAsString()).get(0).size()).isEqualTo(3);
        mvc.perform(get("/api/applications").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void providerApplicationRequiresTheExactAppliedAccessVersion() throws Exception {
        create("managed-app", "https://managed-app.example.test", true);
        expectEmpty();
        jdbc.update("insert into account_application_user_states"
                        + "(user_id, app_code, local_user_id, applied_status, applied_version, last_synced_at)"
                        + " values (?, 'managed-app', '9007199254740993', 'enabled', 2, current_timestamp)", user.getId());
        expectEmpty();
        jdbc.update("update account_application_user_states set applied_version = 1 where user_id = ?", user.getId());
        // JDBC 夹具更新绕过 MyBatis，读取前清除同一测试事务中的查询缓存。
        sqlSession.clearCache();
        mvc.perform(get("/api/session/applications").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].appCode").value("managed-app"));
        jdbc.update("update account_user_applications set version = 2 where user_id = ?", user.getId());
        expectEmpty();
    }

    @Test
    void revokedOrDisabledApplicationsDisappearFromTheNextRead() throws Exception {
        create("withdrawn-app", "", true);
        create("disabled-app", "", true);
        grants.delete(user.getId(), "withdrawn-app");
        jdbc.update("update account_applications set status = 'disabled' where app_code = 'disabled-app'");
        expectEmpty();
    }

    private void create(String appCode, String provider, boolean grant) {
        applications.createManagedApplication(new SaveApplicationCommand(appCode, appCode,
                "https://" + appCode + ".example.test/login", "https://" + appCode + ".example.test/callback",
                "", provider, "default", "synthetic-entry-secret", 0), "system");
        if (grant) {
            grants.insertAccess(user.getId(), appCode, "enabled", "system");
        }
    }

    private void expectEmpty() throws Exception {
        sqlSession.clearCache();
        mvc.perform(get("/api/session/applications").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
}
