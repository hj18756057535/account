package com.hypers.account.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.directory.DirectoryImportStore;
import com.hypers.account.audit.AuditLogService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;

class ApplicationSyncTimingTest {
    @Test
    void anImmediateDeliveryRemainsDueAfterDatabaseTimestampRounding() {
        Instant now = Instant.parse("2026-11-24T00:00:00.123456700Z");
        ApplicationSyncStore store = mock(ApplicationSyncStore.class);
        AccountDirectoryService directory = mock(AccountDirectoryService.class);
        ApplicationSyncPolicy policy = mock(ApplicationSyncPolicy.class);
        AccountApplication app = new AccountApplication("fixture-app", "Fixture", "https://cms.example.test",
                "https://cms.example.test/callback", "", "https://cms.example.test", "synthetic-secret", "default");
        AccountUser user = new AccountUser();
        user.setId("fixture-user");
        user.setAccount("fixture-user");
        user.setStatus("enabled");
        ApplicationSyncDelivery delivery = new ApplicationSyncDelivery();
        delivery.setId("fixture-delivery");
        delivery.setAppCode(app.getAppCode());
        delivery.setUserId(user.getId());
        delivery.setDesiredStatus("enabled");
        delivery.setSyncVersion(1);
        delivery.setCreatedAt(now);
        when(policy.databaseMode()).thenReturn(true);
        when(policy.canDeliver(app)).thenReturn(true);
        when(store.find(delivery.getId())).thenReturn(delivery);
        when(directory.getApplication(app.getAppCode())).thenReturn(app);
        when(directory.getUser(user.getId())).thenReturn(user);
        ApplicationSynchronizationService service = new ApplicationSynchronizationService(store, directory, policy,
                mock(ApplicationSyncTransport.class), new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(now, ZoneOffset.UTC), mock(PlatformTransactionManager.class),
                mock(AuditLogService.class), mock(DirectoryImportStore.class));
        service.created(delivery.getId(), "fixture-admin");

        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:sync_precision_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE fixture_queue (next_retry_at TIMESTAMP(6))");
        jdbc.update("INSERT INTO fixture_queue VALUES (?)", Timestamp.from(delivery.getNextRetryAt()));
        Instant persisted = jdbc.queryForObject("SELECT next_retry_at FROM fixture_queue", Timestamp.class).toInstant();
        assertThat(persisted).isBeforeOrEqualTo(now);
    }
}
