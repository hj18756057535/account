package com.hypers.account.web.management;

import com.hypers.account.app.AccountStore;
import com.hypers.account.app.OverviewCounts;
import com.hypers.account.audit.AuditLogService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ManagementOverviewService {
    private final AccountStore store;
    private final AuditLogService audit;
    private final Clock clock;
    private final Environment environment;

    public Overview overview() {
        Instant sampledAt = clock.instant();
        Instant since = sampledAt.minus(24, ChronoUnit.HOURS);
        OverviewCounts counts = store.overviewCounts(sampledAt.minus(30, ChronoUnit.DAYS));
        long failures = audit.countFailuresSince(since);
        boolean enabled = environment.getProperty("account.web.insights.enabled", Boolean.class, true);
        List<Insight> insights = new ArrayList<>();
        if (enabled) {
            if (counts.getLongDisabledUsers() > 0) insights.add(new Insight("long_disabled_users", counts.getLongDisabledUsers()));
            if (failures >= 3) insights.add(new Insight("repeated_failures", failures));
        }
        return new Overview(sampledAt, since, counts, failures, enabled, insights,
                audit.recentFailures(since).stream()
                        .map(ManagementAuditController.AuditEventResponse::from).toList(),
                "available", "not_checked");
    }

    @Value
    public static class Insight {
        String rule;
        long count;
    }

    @Value
    public static class Overview {
        Instant sampledAt;
        Instant failuresSince;
        OverviewCounts counts;
        long recentFailures;
        boolean insightsEnabled;
        List<Insight> insights;
        List<ManagementAuditController.AuditEventResponse> failureEvents;
        String accountDataStatus;
        String downstreamStatus;
    }
}
