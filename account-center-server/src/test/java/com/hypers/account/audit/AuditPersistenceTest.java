package com.hypers.account.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.hypers.account.app.model.AccountUser;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.AccountUserMapper;
import com.hypers.account.web.AuthController;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest
@Transactional
class AuditPersistenceTest {
    @Autowired
    private AccountUserMapper users;

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void sessionControlsAuditAndUpdatesPreserveCreationMetadata() {
        session("creator");
        AccountUser user = user();
        user.setCreatedBy("untrusted");
        user.setCreatedAt(Instant.EPOCH);
        users.insert(user);
        AccountUser created = users.selectById(user.getId());
        assertThat(created.getCreatedBy()).isEqualTo("creator");
        assertThat(created.getUpdatedBy()).isEqualTo("creator");
        assertThat(created.getCreatedAt()).isNotNull().isAfter(Instant.EPOCH);
        assertThat(created.getUpdatedAt()).isEqualTo(created.getCreatedAt());

        session("editor");
        user.setCreatedBy("replacement");
        user.setUpdatedBy("replacement");
        user.setName("Changed");
        users.update(user);
        AccountUser changed = users.selectById(user.getId());
        assertThat(changed.getName()).isEqualTo("Changed");
        assertThat(changed.getCreatedBy()).isEqualTo(created.getCreatedBy());
        assertThat(changed.getCreatedAt()).isEqualTo(created.getCreatedAt());
        assertThat(changed.getUpdatedBy()).isEqualTo("editor");
        assertThat(changed.getUpdatedAt()).isAfterOrEqualTo(created.getUpdatedAt());
    }

    @Test
    void fillsBatchInsertsAndExplicitlyLabelsBackgroundUpdates() {
        session("importer");
        List<AccountUser> batch = List.of(user(), user());
        users.insertBatch(batch);
        for (AccountUser row : batch) {
            AccountUser actual = users.selectById(row.getId());
            assertThat(actual.getCreatedBy()).isEqualTo("importer");
            assertThat(actual.getUpdatedBy()).isEqualTo("importer");
            assertThat(actual.getCreatedAt()).isNotNull();
            assertThat(actual.getUpdatedAt()).isNotNull();
        }
        RequestContextHolder.resetRequestAttributes();
        users.updateStatus(batch.getFirst().getId(), "disabled");
        AccountUser changed = users.selectById(batch.getFirst().getId());
        assertThat(changed.getCreatedBy()).isEqualTo("importer");
        assertThat(changed.getUpdatedBy()).isEqualTo("system");
    }

    private AccountUser user() {
        String id = UUID.randomUUID().toString();
        return new AccountUser(id, id, "test@example.com", "Test", "123");
    }

    private void session(String actor) {
        var request = new MockHttpServletRequest();
        request.getSession().setAttribute(AuthController.SESSION_USER_KEY, new AccountSessionUser(actor, actor, actor));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
