-- OIDC 授权记录，协议校验由 Spring Authorization Server 执行
create table account_oidc_authorizations (
    id varchar(100) primary key, -- 授权记录标识
    client_id varchar(64) not null, -- 客户端应用编码，关联现有应用主数据
    principal_name varchar(64) not null, -- 全局用户标识，作为稳定主体
    payload text not null, -- 内部授权载荷，含敏感凭据，禁止日志及普通接口访问
    state_hash varchar(64), -- 授权状态参数的 SHA256 摘要
    code_hash varchar(64), -- 授权码 SHA256 摘要
    access_token_hash varchar(64), -- 访问令牌 SHA256 摘要
    id_token_hash varchar(64), -- 身份令牌 SHA256 摘要
    code_redeemed boolean not null default false, -- 授权码是否已失效，原子阻止重复兑换
    created_by varchar(64) not null, -- 创建授权的全局用户标识
    updated_by varchar(64) not null, -- 更新授权的全局用户标识
    created_at timestamp not null, -- 创建时间，UTC
    updated_at timestamp not null -- 更新时间，UTC
);
comment on table account_oidc_authorizations is 'OIDC授权记录，载荷含敏感凭据';
comment on column account_oidc_authorizations.id is '授权记录标识';
comment on column account_oidc_authorizations.client_id is '客户端应用编码，关联现有应用主数据';
comment on column account_oidc_authorizations.principal_name is '全局用户标识，作为稳定主体';
comment on column account_oidc_authorizations.payload is '内部授权载荷，含敏感凭据，禁止日志及普通接口访问';
comment on column account_oidc_authorizations.state_hash is '授权状态参数的 SHA256 摘要';
comment on column account_oidc_authorizations.code_hash is '授权码 SHA256 摘要';
comment on column account_oidc_authorizations.access_token_hash is '访问令牌 SHA256 摘要';
comment on column account_oidc_authorizations.id_token_hash is '身份令牌 SHA256 摘要';
comment on column account_oidc_authorizations.code_redeemed is '授权码是否已失效，原子阻止重复兑换';
comment on column account_oidc_authorizations.created_by is '创建授权的全局用户标识';
comment on column account_oidc_authorizations.updated_by is '更新授权的全局用户标识';
comment on column account_oidc_authorizations.created_at is '创建时间，UTC';
comment on column account_oidc_authorizations.updated_at is '更新时间，UTC';
create unique index uk_account_oidc_code on account_oidc_authorizations (code_hash);
create unique index uk_account_oidc_access on account_oidc_authorizations (access_token_hash);
create unique index uk_account_oidc_id_token on account_oidc_authorizations (id_token_hash);
create unique index uk_account_oidc_state on account_oidc_authorizations (state_hash);
