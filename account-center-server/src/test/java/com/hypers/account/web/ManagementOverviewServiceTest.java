package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.hypers.account.app.AccountStore;
import com.hypers.account.app.InMemoryAccountStore;
import com.hypers.account.app.OverviewCounts;
import com.hypers.account.app.SaveUserCommand;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.web.management.ManagementOverviewService;
import com.hypers.account.web.management.ManagementSecurityAudit;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ManagementOverviewServiceTest {
    @Test
    void disabledInsightsStayEmptyAndTimeWindowUsesInjectedClock() {
        var store = mock(AccountStore.class);
        var audit = mock(AuditLogService.class);
        var now = Instant.parse("2026-10-16T00:00:00Z");
        var counts = new OverviewCounts();
        counts.setMissingCallbacks(2);
        when(store.overviewCounts(now.minusSeconds(30 * 86400L))).thenReturn(counts);
        when(audit.countFailuresSince(now.minusSeconds(86400))).thenReturn(3L);
        when(audit.recentFailures(any())).thenReturn(List.of());
        var service = new ManagementOverviewService(store, audit, Clock.fixed(now, ZoneOffset.UTC),
                new MockEnvironment().withProperty("account.web.insights.enabled", "false"));
        var response = service.overview();
        assertThat(response.getInsights()).isEmpty();
        assertThat(response.getRecentFailures()).isEqualTo(3);
        assertThat(response.getFailuresSince()).isEqualTo(now.minusSeconds(86400));
    }

    @Test
    void memoryCountsRespectDisabledBoundaryAndMissingTimestamp() {
        var store = new InMemoryAccountStore();
        var boundary = Instant.parse("2026-09-16T00:00:00Z");
        var user = store.saveNewUser(new SaveUserCommand("test", "test@example.com", "Test", "123"));
        user.setStatus("disabled");
        user.setUpdatedAt(boundary);
        assertThat(store.overviewCounts(boundary).getLongDisabledUsers()).isZero();
        user.setUpdatedAt(boundary.minusSeconds(1));
        assertThat(store.overviewCounts(boundary).getLongDisabledUsers()).isEqualTo(1);
        user.setUpdatedAt(null);
        assertThat(store.overviewCounts(boundary).getLongDisabledUsers()).isZero();
    }

    @Test
    void auditFailureDoesNotChangeOriginalSecurityDecision() {
        var audit = mock(AuditLogService.class);
        doThrow(new IllegalStateException("private-detail")).when(audit).securityEvent(null, "MANAGEMENT_LOGIN_FAILED", false);
        new ManagementSecurityAudit(audit).record(null, "MANAGEMENT_LOGIN_FAILED", false);
        verify(audit).securityEvent(null, "MANAGEMENT_LOGIN_FAILED", false);
    }
}
