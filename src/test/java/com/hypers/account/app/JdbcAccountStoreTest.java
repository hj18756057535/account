package com.hypers.account.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

class JdbcAccountStoreTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource("jdbc:h2:mem:account_store;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        dataSource = ds;

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table if exists account_user_applications");
        jdbcTemplate.execute("drop table if exists account_applications");
        jdbcTemplate.execute("drop table if exists account_users");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(
                    connection,
                    new EncodedResource(new ClassPathResource("db/schema.sql"), StandardCharsets.UTF_8),
                    false,
                    false,
                    "--",
                    ";",
                    "/*",
                    "*/");
        }
    }

    @Test
    void jdbcStorePersistsUsersApplicationsAndAuthorizationsAcrossInstances() {
        AccountStore writer = new JdbcAccountStore(new JdbcTemplate(dataSource));
        AccountApplication application = writer.saveApplication(new RegisterApplicationCommand(
                "cms-ai",
                "双碳服务",
                "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003",
                "secret",
                "default"));
        AccountUser user = writer.saveNewUser(new SaveUserCommand(
                "zhangsan",
                "zhangsan@example.com",
                "张三",
                "13800000000"));

        writer.authorize(user.getId(), application.getAppCode());

        AccountStore reader = new JdbcAccountStore(new JdbcTemplate(dataSource));

        assertThat(reader.requireUser(user.getId()).getAccount()).isEqualTo("zhangsan");
        assertThat(reader.requireApplication("cms-ai").getDefaultTenantCode()).isEqualTo("default");
        assertThat(reader.isAuthorized(user.getId(), "cms-ai")).isTrue();
        assertThat(reader.findAuthorizedApplications(user.getId()))
                .extracting(AccountApplication::getAppCode)
                .containsExactly("cms-ai");
    }
}
