package com.hypers.account.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.hypers.account.app.model.OverviewCounts;
import com.hypers.account.mapper.AccountUserMapper;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.web.management.service.ManagementOverviewService;
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
        var userMapper = mock(AccountUserMapper.class);
        var audit = mock(AuditLogService.class);
        var now = Instant.parse("2026-10-16T00:00:00Z");
        var counts = new OverviewCounts();
        counts.setLongDisabledUsers(2);
        when(userMapper.selectOverviewCounts(now.minusSeconds(30 * 86400L))).thenReturn(counts);
        when(audit.countFailuresSince(now.minusSeconds(86400))).thenReturn(3L);
        when(audit.recentFailures(any())).thenReturn(List.of());
        var service = new ManagementOverviewService(userMapper, audit, Clock.fixed(now, ZoneOffset.UTC),
                new MockEnvironment().withProperty("account.web.insights.enabled", "false"));
        var response = service.overview();
        assertThat(response.getInsights()).isEmpty();
        assertThat(response.getRecentFailures()).isEqualTo(3);
        assertThat(response.getFailuresSince()).isEqualTo(now.minusSeconds(86400));
    }

    @Test
    void auditFailureDoesNotChangeOriginalSecurityDecision() {
        var audit = mock(AuditLogService.class);
        doThrow(new IllegalStateException("private-detail")).when(audit).securityEvent(null, "MANAGEMENT_LOGIN_FAILED", false);
        new ManagementSecurityAudit(audit).record(null, "MANAGEMENT_LOGIN_FAILED", false);
        verify(audit).securityEvent(null, "MANAGEMENT_LOGIN_FAILED", false);
    }
}
