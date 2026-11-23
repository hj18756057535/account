package com.hypers.account.app;

import lombok.Getter;
import lombok.Setter;

/** 管理概览的聚合计数，不包含用户资料或应用凭据。 */
@Getter
@Setter
public class OverviewCounts {
    private long users;
    private long disabledUsers;
    private long applications;
    private long disabledApplications;
    private long grantedAccess;
    private long missingCallbacks;
    private long longDisabledUsers;
}
