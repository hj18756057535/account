package com.hypers.account.web.management.service;

import com.hypers.account.app.model.OverviewCounts;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.mapper.AccountUserMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * 管理首页概览。
 *
 * <p>统计口径由 {@link AccountUserMapper#selectOverviewCounts} 的单条聚合 SQL 提供，
 * 失败证据来自审计日志；本服务只负责时间窗口取样和洞察规则。
 */
@Service
@RequiredArgsConstructor
public class ManagementOverviewService {
    private final AccountUserMapper userMapper;
    private final AuditLogService audit;
    private final Clock clock;
    private final Environment environment;

    public Overview overview() {
        Instant sampledAt = clock.instant();
        Instant since = sampledAt.minus(24, ChronoUnit.HOURS);
        OverviewCounts counts = userMapper.selectOverviewCounts(sampledAt.minus(30, ChronoUnit.DAYS));
        long failures = audit.countFailuresSince(since);
        boolean enabled = environment.getProperty("account.web.insights.enabled", Boolean.class, true);
        List<Insight> insights = new ArrayList<>();
        if (enabled) {
            if (counts.getLongDisabledUsers() > 0) insights.add(new Insight("long_disabled_users", counts.getLongDisabledUsers()));
            if (failures >= 3) insights.add(new Insight("repeated_failures", failures));
        }
        return new Overview(sampledAt, since, counts, failures, enabled, insights,
                audit.recentFailures(since).stream()
                        .map(AuditEventView::from).toList(),
                "available", "not_checked");
    }

    /** 单条洞察规则及其命中数量。 */
    @Value
    public static class Insight {
        String rule;
        long count;
    }

    /** 概览响应。 */
    @Value
    public static class Overview {
        Instant sampledAt;
        Instant failuresSince;
        OverviewCounts counts;
        long recentFailures;
        boolean insightsEnabled;
        List<Insight> insights;
        List<AuditEventView> failureEvents;
        String accountDataStatus;
        String downstreamStatus;
    }
}
