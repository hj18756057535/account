-- OIDC 授权记录，协议校验由 Spring Authorization Server 执行
create table account_oidc_authorizations (
    id varchar(100) primary key comment '授权记录标识', -- 授权记录标识
    client_id varchar(64) not null comment '客户端应用编码，关联现有应用主数据', -- 客户端应用编码，关联现有应用主数据
    principal_name varchar(64) not null comment '全局用户标识，作为稳定主体', -- 全局用户标识，作为稳定主体
    payload longtext not null comment '内部授权载荷，含敏感凭据，禁止日志及普通接口访问', -- 内部授权载荷，含敏感凭据，禁止日志及普通接口访问
    state_hash varchar(64) comment '授权状态参数的 SHA256 摘要', -- 授权状态参数的 SHA256 摘要
    code_hash varchar(64) comment '授权码 SHA256 摘要', -- 授权码 SHA256 摘要
    access_token_hash varchar(64) comment '访问令牌 SHA256 摘要', -- 访问令牌 SHA256 摘要
    id_token_hash varchar(64) comment '身份令牌 SHA256 摘要', -- 身份令牌 SHA256 摘要
    code_redeemed boolean not null default false comment '授权码是否已失效，原子阻止重复兑换', -- 授权码是否已失效，原子阻止重复兑换
    created_by varchar(64) not null comment '创建授权的全局用户标识', -- 创建授权的全局用户标识
    updated_by varchar(64) not null comment '更新授权的全局用户标识', -- 更新授权的全局用户标识
    created_at timestamp not null comment '创建时间，UTC', -- 创建时间，UTC
    updated_at timestamp not null comment '更新时间，UTC' -- 更新时间，UTC
) engine=InnoDB default charset=utf8mb4 comment='OIDC授权记录，载荷含敏感凭据';
create unique index uk_account_oidc_code on account_oidc_authorizations (code_hash);
create unique index uk_account_oidc_access on account_oidc_authorizations (access_token_hash);
create unique index uk_account_oidc_id_token on account_oidc_authorizations (id_token_hash);
create unique index uk_account_oidc_state on account_oidc_authorizations (state_hash);
