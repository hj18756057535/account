package com.hypers.account.app.directory;

import com.hypers.account.contract.directory.AccountDirectoryOrganization;
import com.hypers.account.contract.directory.AccountDirectoryRole;
import com.hypers.account.contract.directory.AccountDirectoryUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 单个应用的来源目录，不提升为 Account 管理员角色或全局组织。 */
@Getter
@Setter
public class ApplicationDirectorySnapshot {
    /** 来源应用编码 */
    private String appCode;

    /** 完成目录拉取的时间 */
    private Instant capturedAt;

    /** 来源用户及其角色组织关系 */
    private List<AccountDirectoryUser> users = new ArrayList<>();

    /** 应用来源角色 */
    private List<AccountDirectoryRole> roles = new ArrayList<>();

    /** 应用来源组织树 */
    private List<AccountDirectoryOrganization> organizations = new ArrayList<>();
}

