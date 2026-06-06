-- ============================================================
-- V1__init_account_center.sql
-- Account Center 初始化建表脚本
-- 包含：用户表、应用表、授权关联表、操作日志、nonce 防重放、管理 ticket、管理员角色
-- ============================================================

-- 用户主表：Account Center 统一管理的用户身份信息
create table if not exists account_users (
    id varchar(64) primary key,                                          -- 用户唯一标识（UUID）
    account varchar(128) not null,                                       -- 登录账号，全局唯一
    email varchar(255) not null,                                         -- 邮箱地址
    name varchar(128) not null,                                          -- 用户姓名
    phone varchar(64) not null,                                          -- 手机号码
    status varchar(32) not null default 'enabled',                       -- 状态：enabled-启用 disabled-禁用
    created_by varchar(64),                                              -- 创建人
    updated_by varchar(64),                                              -- 最后修改人
    created_at timestamp not null default current_timestamp,             -- 创建时间
    updated_at timestamp,                                                -- 最后修改时间
    constraint uk_account_users_account unique (account)                 -- 账号全局唯一约束
);

-- 应用（子系统）主表：注册到 Account Center 的内部应用
create table if not exists account_applications (
    app_code varchar(64) primary key,                                    -- 应用编码（业务主键）
    name varchar(128) not null,                                          -- 应用名称
    entry_url varchar(512) not null,                                     -- 应用入口地址
    sso_callback_url varchar(512) not null,                              -- SSO 登录回调地址
    permission_iframe_url varchar(512) not null,                         -- iframe 授权页面地址（含 {externalUserId} 占位符）
    notify_base_url varchar(512) not null,                               -- 用户同步通知基地址
    secret varchar(255) not null,                                        -- HMAC 签名密钥
    default_tenant_code varchar(64) not null default 'default',          -- 默认租户编码
    status varchar(32) not null default 'enabled',                       -- 状态：enabled-启用 disabled-禁用
    secret_version integer not null default 1,                           -- 密钥版本号，轮换时递增
    created_by varchar(64),                                              -- 创建人
    updated_by varchar(64),                                              -- 最后修改人
    created_at timestamp not null default current_timestamp,             -- 创建时间
    updated_at timestamp                                                 -- 最后修改时间
);

-- 用户-应用授权关联表：记录哪些用户可以访问哪些应用
create table if not exists account_user_applications (
    user_id varchar(64) not null,                                        -- 用户 ID（关联 account_users.id）
    app_code varchar(64) not null,                                       -- 应用编码（关联 account_applications.app_code）
    status varchar(32) not null default 'enabled',                       -- 授权状态：enabled-有效 disabled-已撤销
    authorized_by varchar(64),                                           -- 授权操作人
    created_at timestamp not null default current_timestamp,             -- 授权时间
    deauthorized_at timestamp,                                           -- 取消授权时间
    primary key (user_id, app_code),
    constraint fk_ua_user foreign key (user_id) references account_users (id),
    constraint fk_ua_app foreign key (app_code) references account_applications (app_code)
);

-- 操作审计日志表：记录所有关键管理操作
create table if not exists account_operation_logs (
    id varchar(64) primary key,                                          -- 日志唯一标识
    operator_id varchar(64),                                             -- 操作人 ID
    operation_type varchar(64) not null,                                 -- 操作类型：CREATE/UPDATE/ENABLE/DISABLE/AUTHORIZE/DEAUTHORIZE 等
    target_type varchar(64) not null,                                    -- 操作对象类型：USER/APPLICATION/SSO_TICKET 等
    target_id varchar(128) not null,                                     -- 操作对象 ID
    detail text,                                                         -- 操作详情（JSON 格式）
    created_at timestamp not null default current_timestamp              -- 操作时间
);

-- Nonce 防重放记录表：用于 HMAC 签名校验的一次性随机数
create table if not exists account_nonce_records (
    nonce varchar(128) primary key,                                      -- 一次性随机数
    app_code varchar(64) not null,                                       -- 所属应用编码
    purpose varchar(64) not null,                                        -- 用途标识
    expires_at timestamp not null,                                       -- 过期时间
    created_at timestamp not null default current_timestamp              -- 创建时间
);

-- 管理端 ticket 表：用于 iframe 授权页面的短期凭证
create table if not exists account_admin_tickets (
    code varchar(128) primary key,                                       -- ticket 编码
    app_code varchar(64) not null,                                       -- 目标应用编码
    user_id varchar(64) not null,                                        -- 关联用户 ID
    purpose varchar(64) not null,                                        -- 用途：iframe-permission 等
    expires_at timestamp not null,                                       -- 过期时间（60 秒有效期）
    used_at timestamp,                                                   -- 使用时间（一次性，使用后标记）
    created_at timestamp not null default current_timestamp              -- 创建时间
);

-- 管理员角色关联表：Account Center 管理后台的角色分配
create table if not exists account_admin_roles (
    user_id varchar(64) not null,                                        -- 用户 ID
    role_code varchar(64) not null,                                      -- 角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR
    created_at timestamp not null default current_timestamp,             -- 分配时间
    primary key (user_id, role_code)
);
