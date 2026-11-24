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
            var source = new DriverManagerDataSource("jdbc:h2:mem:baseline_" + UUID.randomUUID()
                    + ";MODE=" + (vendor.equals("mysql") ? "MySQL" : "PostgreSQL") + ";DB_CLOSE_DELAY=-1", "sa", "");
            var flyway = Flyway.configure().dataSource(source).locations("classpath:db/vendor/" + vendor)
                    .baselineOnMigrate(false).cleanDisabled(true).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
            var jdbc = new JdbcTemplate(source);
            assertThat(jdbc.queryForList("select remarks from information_schema.columns "
                    + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'", String.class))
                    .hasSize(166).allMatch(value -> value != null && value.matches(".*[\\p{IsHan}].*"));
            assertThat(jdbc.queryForList("select table_name from information_schema.tables "
                    + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'", String.class))
                    .hasSize(14).allSatisfy(table -> assertThat(jdbc.queryForList(
                            "select column_name from information_schema.columns where table_schema = 'PUBLIC' and table_name = ?",
                            String.class, table)).contains("CREATED_BY", "UPDATED_BY", "CREATED_AT", "UPDATED_AT"));
            jdbc.update("insert into account_users (id, account, email, name, phone) values (?, ?, ?, ?, ?)",
                    "init-user", "init-user", "test@example.invalid", "Synthetic", "001");
            assertThatThrownBy(() -> jdbc.update("insert into account_users (id, account, email, name, phone) values (?, ?, ?, ?, ?)",
                    "duplicate", "init-user", "test@example.invalid", "Synthetic", "002"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("select account from account_users where id = 'init-user'", String.class))
                    .isEqualTo("init-user");
            flyway.validate();
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
            assertThat(sql.lines().filter(line -> line.startsWith("create table ")).count()).isEqualTo(14);
            assertThat(sql.lines().filter(line -> line.startsWith("create index ")).count()).isEqualTo(8);
            assertThat(sql.toLowerCase()).doesNotContain("alter table", "drop table", "truncate ", "delete from ");
            if (vendor.equals("mysql")) {
                assertThat(sql).contains("rows_json longtext", "result_json longtext");
                assertThat(sql.lines().filter(line -> line.startsWith("    ") && line.contains(" comment '")).count())
                        .isEqualTo(166);
            } else {
                assertThat(sql.lines().filter(line -> line.startsWith("comment on column ")).count()).isEqualTo(166);
            }
        }
    }
}
