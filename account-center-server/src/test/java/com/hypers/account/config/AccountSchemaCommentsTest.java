package com.hypers.account.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AccountSchemaCommentsTest {

    @Test
    void initializesBothDialectsInOneMigrationWithAllCommentsAndConstraints() {
        for (String vendor : new String[]{"postgresql", "mysql"}) {
            String mode = vendor.equals("mysql") ? "MySQL" : "PostgreSQL";
            var source = new DriverManagerDataSource("jdbc:h2:mem:init_" + UUID.randomUUID()
                    + ";MODE=" + mode + ";DB_CLOSE_DELAY=-1", "sa", "");
            var flyway = Flyway.configure().dataSource(source).locations("classpath:db/vendor/" + vendor)
                    .baselineOnMigrate(false).target("1").load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
            var jdbc = new JdbcTemplate(source);
            var columns = jdbc.queryForList("select remarks from information_schema.columns "
                    + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'", String.class);
            assertThat(columns).hasSize(99).allMatch(value -> value != null && value.matches(".*[\\p{IsHan}].*"));
            var tables = jdbc.queryForList("select remarks from information_schema.tables "
                    + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'", String.class);
            assertThat(tables).hasSize(10).allMatch(value -> value != null && value.matches(".*[\\p{IsHan}].*"));
            jdbc.update("insert into account_users (id, account, email, name, phone) values (?, ?, ?, ?, ?)",
                    "init-user", "init-user", "test@example.invalid", "Synthetic", "001");
            var before = jdbc.queryForMap("select * from account_users where id = 'init-user'");
            assertThat(before.get("VERSION")).isEqualTo(1L);
            assertThat(before.get("PASSWORD")).isNull();
            assertThatThrownBy(() -> jdbc.update("insert into account_users (id, account, email, name, phone) "
                    + "values ('duplicate', 'init-user', 'test@example.invalid', 'Synthetic', '001')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into account_user_applications (user_id, app_code) "
                    + "values ('missing', 'missing')")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("insert into account_user_imports "
                    + "(id, file_hash, row_count, valid, status, expires_at, created_by) "
                    + "values ('invalid', 'hash', 0, false, 'preview', current_timestamp, 'init-user')"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForMap("select * from account_users where id = 'init-user'")).isEqualTo(before);
            flyway.validate();
            var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/vendor/" + vendor).load();
            assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(upgrade.info().current().getVersion().getVersion()).isEqualTo("2");
            assertThat(jdbc.queryForMap("select * from account_users where id = 'init-user'")).isEqualTo(before);
            assertThat(jdbc.queryForList("select remarks from information_schema.columns "
                    + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'", String.class))
                    .hasSize(116).allMatch(value -> value != null && value.matches(".*[\\p{IsHan}].*"));
            assertThat(jdbc.queryForList("select remarks from information_schema.tables "
                    + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'", String.class))
                    .hasSize(11).allMatch(value -> value != null && value.matches(".*[\\p{IsHan}].*"));
            assertThat(upgrade.migrate().migrationsExecuted).isZero();
            upgrade.validate();
        }
    }

    @Test
    void selectsOnlyOneDialectAndDisablesImplicitBaselineAndClean() throws Exception {
        for (String product : new String[]{"PostgreSQL", "H2", "MySQL"}) {
            var source = mock(DataSource.class);
            var connection = mock(Connection.class);
            var metadata = mock(DatabaseMetaData.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getDatabaseProductName()).thenReturn(product);
            var configuration = Flyway.configure().dataSource(source).locations("classpath:db/migration")
                    .baselineOnMigrate(true).cleanDisabled(false);
            var customizer = new AccountFlywayConfiguration().accountMigrations();
            customizer.customize(configuration);
            customizer.customize(configuration);
            assertThat(configuration.getLocations()).extracting(Location::getDescriptor).containsExactly(
                    "classpath:db/vendor/" + (product.equals("MySQL") ? "mysql" : "postgresql"));
            assertThat(configuration.isBaselineOnMigrate()).isFalse();
            assertThat(configuration.isCleanDisabled()).isTrue();
        }
    }

    @Test
    void refusesExistingUnversionedSchemaWithoutChangingData() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:nonempty_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("create table existing_test_data (id integer primary key)");
        jdbc.update("insert into existing_test_data values (1)");
        var configuration = Flyway.configure().dataSource(source).baselineOnMigrate(true);
        new AccountFlywayConfiguration().accountMigrations().customize(configuration);
        assertThatThrownBy(() -> configuration.load().migrate())
                .isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        assertThat(jdbc.queryForObject("select count(*) from existing_test_data", Integer.class)).isEqualTo(1);
    }

    @Test
    void initializationHasNoFollowupAlterOrDestructiveStatements() throws Exception {
        for (String vendor : new String[]{"postgresql", "mysql"}) {
            String sql = new ClassPathResource("db/vendor/" + vendor + "/V1__init_account_center.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            assertThat(sql.lines().filter(line -> line.startsWith("create table ")).count()).isEqualTo(10);
            assertThat(sql.lines().filter(line -> line.startsWith("create index ")).count()).isEqualTo(5);
            assertThat(sql.toLowerCase()).doesNotContain("alter table", "drop table", "truncate ", "delete from ");
            if (vendor.equals("mysql")) {
                assertThat(sql).contains("rows_json longtext", "result_json longtext");
                assertThat(sql.lines().filter(line -> line.startsWith("    ") && line.contains(" comment '")).count())
                        .isEqualTo(99);
            } else {
                assertThat(sql.lines().filter(line -> line.startsWith("comment on column ")).count()).isEqualTo(99);
            }
        }
    }
}
