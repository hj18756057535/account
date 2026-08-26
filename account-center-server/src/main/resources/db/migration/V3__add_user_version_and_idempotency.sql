-- 用户管理写接口的乐观锁与持久化幂等记录。
alter table account_users add column version bigint not null default 1;

create table account_idempotency_records (
    id varchar(64) primary key,
    idempotency_key varchar(128) not null,
    operator_id varchar(64) not null,
    request_method varchar(16) not null,
    request_path varchar(512) not null,
    request_hash varchar(64) not null,
    status varchar(32) not null,
    response_status integer,
    response_body text,
    created_at timestamp not null default current_timestamp,
    expires_at timestamp not null,
    constraint uk_account_idempotency_scope unique
        (operator_id, request_method, request_path, idempotency_key)
);

create index idx_account_idempotency_expires
    on account_idempotency_records (expires_at);
