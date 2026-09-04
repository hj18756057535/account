-- 应用管理版本、协议能力、Secret 状态与用户准入期望状态。
alter table account_applications add column version bigint not null default 1;
alter table account_applications add column protocol_capabilities varchar(255) not null default 'sso,admin_ticket,user_sync';
alter table account_applications add column secret_state varchar(32) not null default 'active';

alter table account_user_applications add column desired_status varchar(32) not null default 'enabled';
alter table account_user_applications add column version bigint not null default 1;
alter table account_user_applications add column updated_by varchar(64);
alter table account_user_applications add column updated_at timestamp;

create table account_sync_commands (
    id varchar(64) primary key,
    idempotency_key varchar(128) not null,
    user_id varchar(64) not null,
    app_code varchar(64) not null,
    desired_status varchar(32) not null,
    sync_version bigint not null,
    status varchar(32) not null,
    attempts integer not null default 0,
    next_retry_at timestamp,
    error_code varchar(128),
    trace_id varchar(64) not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp,
    constraint uk_account_sync_command_idempotency unique (idempotency_key),
    constraint fk_sync_command_user foreign key (user_id) references account_users (id),
    constraint fk_sync_command_app foreign key (app_code) references account_applications (app_code)
);

create index idx_account_sync_commands_pending
    on account_sync_commands (status, created_at);
