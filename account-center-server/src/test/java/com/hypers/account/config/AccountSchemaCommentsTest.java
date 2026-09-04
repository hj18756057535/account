package com.hypers.account.config;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class AccountSchemaCommentsTest {

    @Test
    void upgradesExistingDatabaseWithoutChangingColumnsOrData() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:comments_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("5").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("insert into account_users (id, account, email, name, phone) values (?, ?, ?, ?, ?)",
                "comment-test", "comment-test", "test@example.invalid", "注释迁移测试", "synthetic");
        String structureQuery = "select table_name, column_name, data_type, is_nullable, column_default, "
                + "character_maximum_length from information_schema.columns "
                + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT\\_%' escape '\\' "
                + "order by table_name, ordinal_position";
        var beforeColumns = jdbc.queryForList(structureQuery);
        var beforeUser = jdbc.queryForMap("select * from account_users where id = 'comment-test'");
        var beforeConstraints = jdbc.queryForList("select * from information_schema.table_constraints "
                + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'");
        var configuration = Flyway.configure().dataSource(source).locations("classpath:db/migration");
        new AccountFlywayConfiguration().accountCommentMigrations().customize(configuration);
        var flyway = configuration.load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");
        assertThat(jdbc.queryForList(structureQuery)).isEqualTo(beforeColumns);
        assertThat(jdbc.queryForMap("select * from account_users where id = 'comment-test'")).isEqualTo(beforeUser);
        assertThat(jdbc.queryForList("select * from information_schema.table_constraints "
                + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'"))
                .containsExactlyInAnyOrderElementsOf(beforeConstraints);
        var columnComments = jdbc.queryForList("select remarks from information_schema.columns "
                + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'", String.class);
        assertThat(columnComments).hasSize(87).allMatch(value -> value != null && value.matches(".*[\\p{IsHan}].*"));
        var tableComments = jdbc.queryForList("select remarks from information_schema.tables "
                + "where table_schema = 'PUBLIC' and table_name like 'ACCOUNT%'", String.class);
        assertThat(tableComments).hasSize(9).allMatch(value -> value != null && value.matches(".*[\\p{IsHan}].*"));
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
    }

    @Test
    void selectsOnlyOneDialectAndPreservesConfiguredLocations() throws Exception {
        for (String product : new String[] {"PostgreSQL", "H2", "MySQL"}) {
            var source = mock(DataSource.class);
            var connection = mock(Connection.class);
            var metadata = mock(DatabaseMetaData.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getDatabaseProductName()).thenReturn(product);
            var configuration = Flyway.configure().dataSource(source).locations("classpath:db/migration");
            var customizer = new AccountFlywayConfiguration().accountCommentMigrations();
            customizer.customize(configuration);
            customizer.customize(configuration);
            assertThat(configuration.getLocations()).extracting(Location::getDescriptor).containsExactly(
                    "classpath:db/migration", "classpath:db/vendor/" + (product.equals("MySQL") ? "mysql" : "postgresql"));
        }
    }

    @Test
    void bothDialectScriptsCoverTheSameNumberOfComments() throws Exception {
        String postgres = new ClassPathResource("db/vendor/postgresql/V6__add_schema_comments.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        String mysql = new ClassPathResource("db/vendor/mysql/V6__add_schema_comments.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(postgres.lines().filter(line -> line.startsWith("COMMENT ON TABLE ")).count()).isEqualTo(9);
        assertThat(postgres.lines().filter(line -> line.startsWith("COMMENT ON COLUMN ")).count()).isEqualTo(87);
        assertThat(mysql.lines().filter(line -> line.stripLeading().startsWith("MODIFY COLUMN ")).count()).isEqualTo(87);
        assertThat(mysql.lines().filter(line -> line.stripLeading().startsWith("COMMENT = ")).count()).isEqualTo(9);
        assertThat(mysql.lines().filter(line -> line.contains("MODIFY COLUMN ")))
                .allMatch(line -> line.matches(".* COMMENT '[^']*[\\p{IsHan}][^']*', -- .+"));
        assertThat(mysql).contains("ALGORITHM = INPLACE, LOCK = NONE");
    }
}
