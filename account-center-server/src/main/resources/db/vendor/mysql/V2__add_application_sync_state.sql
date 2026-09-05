-- 应用准入同步确认；增量升级完整 V1，保留已有用户与应用数据。

-- 应用用户映射与可靠准入确认
create table account_application_user_states (
    user_id varchar(64) not null comment '全局用户标识，关联账号中心用户', -- 全局用户标识，关联账号中心用户
    app_code varchar(64) not null comment '所属应用编码', -- 所属应用编码
    local_user_id varchar(255) not null comment '应用确认的本地用户标识，不按账号猜测', -- 应用确认的本地用户标识，不按账号猜测
    applied_status varchar(32) not null comment '已确认准入状态 enabled 或 disabled', -- 已确认准入状态 enabled 或 disabled
    applied_version bigint not null comment '已确认准入版本，必须匹配投递命令', -- 已确认准入版本，必须匹配投递命令
    last_synced_at timestamp not null comment '最近确认时间 UTC', -- 最近确认时间 UTC
    created_by varchar(64) comment '首次确认关联的操作人', -- 首次确认关联的操作人
    updated_by varchar(64) comment '最近确认关联的操作人', -- 最近确认关联的操作人
    created_at timestamp not null default current_timestamp comment '创建时间 UTC', -- 创建时间 UTC
    updated_at timestamp null comment '最近更新时间 UTC', -- 最近更新时间 UTC
    primary key (user_id, app_code),
    constraint uk_app_local_user unique (app_code, local_user_id),
    constraint fk_app_state_user foreign key (user_id) references account_users (id),
    constraint fk_app_state_app foreign key (app_code) references account_applications (app_code),
    constraint ck_app_state_status check (applied_status in ('enabled', 'disabled')),
    constraint ck_app_state_version check (applied_version > 0)
) engine=InnoDB default charset=utf8mb4 comment='应用用户映射与可靠准入确认';

alter table account_sync_commands add column payload_json longtext comment '冻结的用户同步请求，终态或保留到期后清除'; -- 冻结的用户同步请求，终态或保留到期后清除

alter table account_sync_commands add column payload_hash varchar(64) comment '冻结请求 SHA-256 摘要'; -- 冻结请求 SHA-256 摘要

alter table account_sync_commands add column operator_id varchar(64) comment '发起当前命令的管理员标识'; -- 发起当前命令的管理员标识

alter table account_sync_commands add column target_application_version bigint comment '冻结目标应用配置版本'; -- 冻结目标应用配置版本

alter table account_sync_commands add column lease_token varchar(64) comment '当前处理租约随机标识，防止过时提交'; -- 当前处理租约随机标识，防止过时提交

alter table account_sync_commands add column lease_expires_at timestamp null comment '处理租约到期时间 UTC'; -- 处理租约到期时间 UTC

alter table account_applications add column managed_sync boolean not null default false comment '已纳入持久化同步管理，不随投递开关关闭而回退'; -- 已纳入持久化同步管理，不随投递开关关闭而回退

create index idx_sync_retry on account_sync_commands (status, next_retry_at);
create index idx_sync_access_version on account_sync_commands (user_id, app_code, sync_version);
