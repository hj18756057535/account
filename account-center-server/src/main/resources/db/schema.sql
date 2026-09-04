-- Account Center 当前建表参考（V1～V5 合并视图，2026-09-04）
-- 仅用于结构审阅与字段说明，不是应用启动入口，也不是 Flyway 迁移。
-- 新建/升级数据库只运行 db/migration 版本迁移，不要同时执行本参考文件。
-- 下列 -- 中文注释属于 SQL 源码说明，不会写入数据库字段 COMMENT 元数据。
-- 数据库元数据注释由 db/vendor 下的 V6 增量迁移补齐；历史 V1～V5 保持原样。

-- 全局用户
create table account_users (
    id varchar(64) primary key, -- 用户唯一标识（UUID）
    account varchar(128) not null, -- 登录账号，全局唯一
    email varchar(255) not null, -- 邮箱地址
    name varchar(128) not null, -- 用户姓名
    phone varchar(64) not null, -- 手机号码
    status varchar(32) not null default 'enabled', -- 状态：enabled-启用 disabled-禁用
    created_by varchar(64), -- 创建人
    updated_by varchar(64), -- 最后修改人
    created_at timestamp not null default current_timestamp, -- 创建时间
    updated_at timestamp, -- 最后修改时间
    password varchar(255), -- 登录密码 BCrypt 哈希；允许为空，禁止保存明文
    version bigint not null default 1, -- 资源乐观锁版本；初始为 1，每次更新递增
    constraint uk_account_users_account unique (account)
);

-- 应用登记
create table account_applications (
    app_code varchar(64) primary key, -- 应用编码（业务主键）
    name varchar(128) not null, -- 应用名称
    entry_url varchar(512) not null, -- 应用入口地址
    sso_callback_url varchar(512) not null, -- SSO 登录回调地址
    permission_iframe_url varchar(512) not null, -- iframe 授权页面地址（含 {externalUserId} 占位符）
    notify_base_url varchar(512) not null, -- 用户同步通知基地址
    secret varchar(255) not null, -- 受保护的 HMAC 签名密钥；不得进入普通响应、日志或文档示例
    default_tenant_code varchar(64) not null default 'default', -- 默认租户编码
    status varchar(32) not null default 'enabled', -- 状态：enabled-启用 disabled-禁用
    secret_version integer not null default 1, -- 密钥版本号，轮换时递增
    created_by varchar(64), -- 创建人
    updated_by varchar(64), -- 最后修改人
    created_at timestamp not null default current_timestamp, -- 创建时间
    updated_at timestamp, -- 最后修改时间
    version bigint not null default 1, -- 资源乐观锁版本；初始为 1，每次更新递增
    protocol_capabilities varchar(255) not null default 'sso,admin_ticket,user_sync', -- 协议能力编码列表，逗号分隔：sso、admin_ticket、user_sync
    secret_state varchar(32) not null default 'active' -- 密钥状态：active 可用、revoked 已撤销
);

-- 用户应用准入
create table account_user_applications (
    user_id varchar(64) not null, -- 用户 ID（关联 account_users.id）
    app_code varchar(64) not null, -- 应用编码（关联 account_applications.app_code）
    status varchar(32) not null default 'enabled', -- 授权状态：enabled-有效 disabled-已撤销
    authorized_by varchar(64), -- 授权操作人
    created_at timestamp not null default current_timestamp, -- 授权时间
    deauthorized_at timestamp, -- 取消授权时间
    desired_status varchar(32) not null default 'enabled', -- Account 期望准入状态：enabled / disabled；不代表业务应用已同步
    version bigint not null default 1, -- 资源乐观锁版本；初始为 1，每次更新递增
    updated_by varchar(64), -- 最后修改人 ID
    updated_at timestamp, -- 最后修改时间
    primary key (user_id, app_code),
    constraint fk_ua_user foreign key (user_id) references account_users (id),
    constraint fk_ua_app foreign key (app_code) references account_applications (app_code)
);

-- 操作审计日志
create table account_operation_logs (
    id varchar(64) primary key, -- 日志唯一标识
    operator_id varchar(64), -- 操作人 ID
    operation_type varchar(64) not null, -- 稳定操作编码，如 USER_CREATED / APPLICATION_ACCESS_CHANGED；不随展示语言变化
    target_type varchar(64) not null, -- 操作对象类型：USER/APPLICATION/SSO_TICKET 等
    target_id varchar(128) not null, -- 操作对象 ID
    detail text, -- 脱敏操作摘要 JSON；禁止保存密码、Secret、票据或个人敏感值
    created_at timestamp not null default current_timestamp, -- 操作时间
    trace_id varchar(64), -- 服务端请求关联 ID；历史记录或非请求调用允许为空
    outcome varchar(16) not null default 'unknown' -- 结果：success 成功、failure 失败、unknown 历史未记录
);

-- HMAC Nonce 防重放记录
create table account_nonce_records (
    nonce varchar(128) primary key, -- 一次性随机数
    app_code varchar(64) not null, -- 所属应用编码
    purpose varchar(64) not null, -- 用途标识
    expires_at timestamp not null, -- 过期时间
    created_at timestamp not null default current_timestamp -- 创建时间
);

-- 管理端一次性票据
create table account_admin_tickets (
    code varchar(128) primary key, -- ticket 编码
    app_code varchar(64) not null, -- 目标应用编码
    user_id varchar(64) not null, -- 关联用户 ID
    purpose varchar(64) not null, -- 用途：iframe-permission 等
    expires_at timestamp not null, -- 票据过期时间；有效期由服务端配置决定
    used_at timestamp, -- 使用时间（一次性，使用后标记）
    created_at timestamp not null default current_timestamp -- 创建时间
);

-- 管理角色分配
create table account_admin_roles (
    user_id varchar(64) not null, -- 用户 ID
    role_code varchar(64) not null, -- 角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR
    created_at timestamp not null default current_timestamp, -- 分配时间
    primary key (user_id, role_code)
);

-- 管理写请求幂等记录
create table account_idempotency_records (
    id varchar(64) primary key, -- 记录唯一标识
    idempotency_key varchar(128) not null, -- 客户端幂等键；与操作者、HTTP 方法和路径共同限定唯一请求
    operator_id varchar(64) not null, -- 操作人用户 ID
    request_method varchar(16) not null, -- 请求 HTTP 方法
    request_path varchar(512) not null, -- 规范化资源路径
    request_hash varchar(64) not null, -- 请求内容摘要；拒绝幂等键相同而内容不同的请求
    status varchar(32) not null, -- 幂等状态：processing 处理中、completed 已完成
    response_status integer, -- 完成后的 HTTP 状态码；处理中为空
    response_body text, -- 可重放响应 JSON；敏感结果不持久化，保存空 JSON 对象
    created_at timestamp not null default current_timestamp, -- 创建时间
    expires_at timestamp not null, -- 过期时间；保留期限由服务端定义
    constraint uk_account_idempotency_scope unique (operator_id, request_method, request_path, idempotency_key)
);

-- 应用准入同步命令
create table account_sync_commands (
    id varchar(64) primary key, -- 记录唯一标识
    idempotency_key varchar(128) not null, -- 命令级幂等键，全局唯一
    user_id varchar(64) not null, -- 目标用户 ID，关联 account_users.id
    app_code varchar(64) not null, -- 目标应用编码，关联 account_applications.app_code
    desired_status varchar(32) not null, -- Account 期望准入状态：enabled / disabled；不代表业务应用已同步
    sync_version bigint not null, -- 命令对应的准入版本，用于幂等和乱序保护
    status varchar(32) not null, -- 命令状态；当前为 pending_application_adaptation，尚未实际投递
    attempts integer not null default 0, -- 实际投递尝试次数，初始为 0
    next_retry_at timestamp, -- 下次重试时间；尚未安排时为空
    error_code varchar(128), -- 最近失败的稳定错误码；禁止保存敏感错误详情
    trace_id varchar(64) not null, -- 服务端关联 ID；用于请求或命令追踪
    created_at timestamp not null default current_timestamp, -- 创建时间
    updated_at timestamp, -- 最后修改时间
    constraint uk_account_sync_command_idempotency unique (idempotency_key),
    constraint fk_sync_command_user foreign key (user_id) references account_users (id),
    constraint fk_sync_command_app foreign key (app_code) references account_applications (app_code)
);

-- 查询与过期清理索引（来自 V3～V5）
create index idx_account_idempotency_expires on account_idempotency_records (expires_at);
create index idx_account_sync_commands_pending on account_sync_commands (status, created_at);
create index idx_account_audit_page on account_operation_logs (created_at, id);
create index idx_account_audit_trace on account_operation_logs (trace_id);
