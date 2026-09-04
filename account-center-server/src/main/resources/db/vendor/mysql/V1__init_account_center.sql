-- Account Center 完整初始化基线；仅用于空库，由 Flyway 执行并记录 V1。
-- 已获开发者批准重建未发布测试库；本文件不清库、不迁移旧数据、不插入环境数据。

-- 全局用户
create table account_users (
    id varchar(64) primary key comment '用户唯一标识（UUID）', -- 用户唯一标识（UUID）
    account varchar(128) not null comment '登录账号，全局唯一', -- 登录账号，全局唯一
    email varchar(255) not null comment '邮箱地址', -- 邮箱地址
    name varchar(128) not null comment '用户姓名', -- 用户姓名
    phone varchar(64) not null comment '手机号码', -- 手机号码
    status varchar(32) not null default 'enabled' comment '状态：enabled-启用 disabled-禁用', -- 状态：enabled-启用 disabled-禁用
    created_by varchar(64) comment '创建人', -- 创建人
    updated_by varchar(64) comment '最后修改人', -- 最后修改人
    created_at timestamp not null default current_timestamp comment '创建时间', -- 创建时间
    updated_at timestamp null comment '最后修改时间', -- 最后修改时间
    password varchar(255) comment '登录密码 BCrypt 哈希；允许为空，禁止保存明文', -- 登录密码 BCrypt 哈希；允许为空，禁止保存明文
    version bigint not null default 1 comment '资源乐观锁版本；初始为 1，每次更新递增', -- 资源乐观锁版本；初始为 1，每次更新递增
    constraint uk_account_users_account unique (account)
) engine=InnoDB default charset=utf8mb4 comment='全局用户';

-- 应用登记
create table account_applications (
    app_code varchar(64) primary key comment '应用编码（业务主键）', -- 应用编码（业务主键）
    name varchar(128) not null comment '应用名称', -- 应用名称
    entry_url varchar(512) not null comment '应用入口地址', -- 应用入口地址
    sso_callback_url varchar(512) not null comment 'SSO 登录回调地址', -- SSO 登录回调地址
    permission_iframe_url varchar(512) not null comment 'iframe 授权页面地址（含 {externalUserId} 占位符）', -- iframe 授权页面地址（含 {externalUserId} 占位符）
    notify_base_url varchar(512) not null comment '用户同步通知基地址', -- 用户同步通知基地址
    secret varchar(255) not null comment '受保护的 HMAC 签名密钥；不得进入普通响应、日志或文档示例', -- 受保护的 HMAC 签名密钥；不得进入普通响应、日志或文档示例
    default_tenant_code varchar(64) not null default 'default' comment '默认租户编码', -- 默认租户编码
    status varchar(32) not null default 'enabled' comment '状态：enabled-启用 disabled-禁用', -- 状态：enabled-启用 disabled-禁用
    secret_version integer not null default 1 comment '密钥版本号，轮换时递增', -- 密钥版本号，轮换时递增
    created_by varchar(64) comment '创建人', -- 创建人
    updated_by varchar(64) comment '最后修改人', -- 最后修改人
    created_at timestamp not null default current_timestamp comment '创建时间', -- 创建时间
    updated_at timestamp null comment '最后修改时间', -- 最后修改时间
    version bigint not null default 1 comment '资源乐观锁版本；初始为 1，每次更新递增', -- 资源乐观锁版本；初始为 1，每次更新递增
    protocol_capabilities varchar(255) not null default 'sso,admin_ticket,user_sync' comment '协议能力编码列表，逗号分隔：sso、admin_ticket、user_sync', -- 协议能力编码列表，逗号分隔：sso、admin_ticket、user_sync
    secret_state varchar(32) not null default 'active' comment '密钥状态：active 可用、revoked 已撤销' -- 密钥状态：active 可用、revoked 已撤销
) engine=InnoDB default charset=utf8mb4 comment='应用登记';

-- 用户应用准入
create table account_user_applications (
    user_id varchar(64) not null comment '用户 ID（关联 account_users.id）', -- 用户 ID（关联 account_users.id）
    app_code varchar(64) not null comment '应用编码（关联 account_applications.app_code）', -- 应用编码（关联 account_applications.app_code）
    status varchar(32) not null default 'enabled' comment '授权状态：enabled-有效 disabled-已撤销', -- 授权状态：enabled-有效 disabled-已撤销
    authorized_by varchar(64) comment '授权操作人', -- 授权操作人
    created_at timestamp not null default current_timestamp comment '授权时间', -- 授权时间
    deauthorized_at timestamp null comment '取消授权时间', -- 取消授权时间
    desired_status varchar(32) not null default 'enabled' comment 'Account 期望准入状态：enabled / disabled；不代表业务应用已同步', -- Account 期望准入状态：enabled / disabled；不代表业务应用已同步
    version bigint not null default 1 comment '资源乐观锁版本；初始为 1，每次更新递增', -- 资源乐观锁版本；初始为 1，每次更新递增
    updated_by varchar(64) comment '最后修改人 ID', -- 最后修改人 ID
    updated_at timestamp null comment '最后修改时间', -- 最后修改时间
    primary key (user_id, app_code),
    constraint fk_ua_user foreign key (user_id) references account_users (id),
    constraint fk_ua_app foreign key (app_code) references account_applications (app_code)
) engine=InnoDB default charset=utf8mb4 comment='用户应用准入';

-- 操作审计日志
create table account_operation_logs (
    id varchar(64) primary key comment '日志唯一标识', -- 日志唯一标识
    operator_id varchar(64) comment '操作人 ID', -- 操作人 ID
    operation_type varchar(64) not null comment '稳定操作编码，如 USER_CREATED / APPLICATION_ACCESS_CHANGED；不随展示语言变化', -- 稳定操作编码，如 USER_CREATED / APPLICATION_ACCESS_CHANGED；不随展示语言变化
    target_type varchar(64) not null comment '操作对象类型：USER/APPLICATION/SSO_TICKET 等', -- 操作对象类型：USER/APPLICATION/SSO_TICKET 等
    target_id varchar(128) not null comment '操作对象 ID', -- 操作对象 ID
    detail text comment '脱敏操作摘要 JSON；禁止保存密码、Secret、票据或个人敏感值', -- 脱敏操作摘要 JSON；禁止保存密码、Secret、票据或个人敏感值
    created_at timestamp not null default current_timestamp comment '操作时间', -- 操作时间
    trace_id varchar(64) comment '服务端请求关联 ID；历史记录或非请求调用允许为空', -- 服务端请求关联 ID；历史记录或非请求调用允许为空
    outcome varchar(16) not null default 'unknown' comment '结果：success 成功、failure 失败、unknown 历史未记录' -- 结果：success 成功、failure 失败、unknown 历史未记录
) engine=InnoDB default charset=utf8mb4 comment='操作审计日志';

-- HMAC Nonce 防重放记录
create table account_nonce_records (
    nonce varchar(128) primary key comment '一次性随机数', -- 一次性随机数
    app_code varchar(64) not null comment '所属应用编码', -- 所属应用编码
    purpose varchar(64) not null comment '用途标识', -- 用途标识
    expires_at timestamp not null comment '过期时间', -- 过期时间
    created_at timestamp not null default current_timestamp comment '创建时间' -- 创建时间
) engine=InnoDB default charset=utf8mb4 comment='HMAC Nonce 防重放记录';

-- 管理端一次性票据
create table account_admin_tickets (
    code varchar(128) primary key comment 'ticket 编码', -- ticket 编码
    app_code varchar(64) not null comment '目标应用编码', -- 目标应用编码
    user_id varchar(64) not null comment '关联用户 ID', -- 关联用户 ID
    purpose varchar(64) not null comment '用途：iframe-permission 等', -- 用途：iframe-permission 等
    expires_at timestamp not null comment '票据过期时间；有效期由服务端配置决定', -- 票据过期时间；有效期由服务端配置决定
    used_at timestamp null comment '使用时间（一次性，使用后标记）', -- 使用时间（一次性，使用后标记）
    created_at timestamp not null default current_timestamp comment '创建时间' -- 创建时间
) engine=InnoDB default charset=utf8mb4 comment='管理端一次性票据';

-- 管理角色分配
create table account_admin_roles (
    user_id varchar(64) not null comment '用户 ID', -- 用户 ID
    role_code varchar(64) not null comment '角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR', -- 角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR
    created_at timestamp not null default current_timestamp comment '分配时间', -- 分配时间
    primary key (user_id, role_code)
) engine=InnoDB default charset=utf8mb4 comment='管理角色分配';

-- 管理写请求幂等记录
create table account_idempotency_records (
    id varchar(64) primary key comment '记录唯一标识', -- 记录唯一标识
    idempotency_key varchar(128) not null comment '客户端幂等键；与操作者、HTTP 方法和路径共同限定唯一请求', -- 客户端幂等键；与操作者、HTTP 方法和路径共同限定唯一请求
    operator_id varchar(64) not null comment '操作人用户 ID', -- 操作人用户 ID
    request_method varchar(16) not null comment '请求 HTTP 方法', -- 请求 HTTP 方法
    request_path varchar(512) not null comment '规范化资源路径', -- 规范化资源路径
    request_hash varchar(64) not null comment '请求内容摘要；拒绝幂等键相同而内容不同的请求', -- 请求内容摘要；拒绝幂等键相同而内容不同的请求
    status varchar(32) not null comment '幂等状态：processing 处理中、completed 已完成', -- 幂等状态：processing 处理中、completed 已完成
    response_status integer comment '完成后的 HTTP 状态码；处理中为空', -- 完成后的 HTTP 状态码；处理中为空
    response_body text comment '可重放响应 JSON；敏感结果不持久化，保存空 JSON 对象', -- 可重放响应 JSON；敏感结果不持久化，保存空 JSON 对象
    created_at timestamp not null default current_timestamp comment '创建时间', -- 创建时间
    expires_at timestamp not null comment '过期时间；保留期限由服务端定义', -- 过期时间；保留期限由服务端定义
    constraint uk_account_idempotency_scope unique (operator_id, request_method, request_path, idempotency_key)
) engine=InnoDB default charset=utf8mb4 comment='管理写请求幂等记录';

-- 应用准入同步命令
create table account_sync_commands (
    id varchar(64) primary key comment '记录唯一标识', -- 记录唯一标识
    idempotency_key varchar(128) not null comment '命令级幂等键，全局唯一', -- 命令级幂等键，全局唯一
    user_id varchar(64) not null comment '目标用户 ID，关联 account_users.id', -- 目标用户 ID，关联 account_users.id
    app_code varchar(64) not null comment '目标应用编码，关联 account_applications.app_code', -- 目标应用编码，关联 account_applications.app_code
    desired_status varchar(32) not null comment 'Account 期望准入状态：enabled / disabled；不代表业务应用已同步', -- Account 期望准入状态：enabled / disabled；不代表业务应用已同步
    sync_version bigint not null comment '命令对应的准入版本，用于幂等和乱序保护', -- 命令对应的准入版本，用于幂等和乱序保护
    status varchar(32) not null comment '命令状态；当前为 pending_application_adaptation，尚未实际投递', -- 命令状态；当前为 pending_application_adaptation，尚未实际投递
    attempts integer not null default 0 comment '实际投递尝试次数，初始为 0', -- 实际投递尝试次数，初始为 0
    next_retry_at timestamp null comment '下次重试时间；尚未安排时为空', -- 下次重试时间；尚未安排时为空
    error_code varchar(128) comment '最近失败的稳定错误码；禁止保存敏感错误详情', -- 最近失败的稳定错误码；禁止保存敏感错误详情
    trace_id varchar(64) not null comment '服务端关联 ID；用于请求或命令追踪', -- 服务端关联 ID；用于请求或命令追踪
    created_at timestamp not null default current_timestamp comment '创建时间', -- 创建时间
    updated_at timestamp null comment '最后修改时间', -- 最后修改时间
    constraint uk_account_sync_command_idempotency unique (idempotency_key),
    constraint fk_sync_command_user foreign key (user_id) references account_users (id),
    constraint fk_sync_command_app foreign key (app_code) references account_applications (app_code)
) engine=InnoDB default charset=utf8mb4 comment='应用准入同步命令';

-- 查询与过期清理索引
create index idx_account_idempotency_expires on account_idempotency_records (expires_at);
create index idx_account_sync_commands_pending on account_sync_commands (status, created_at);
create index idx_account_audit_page on account_operation_logs (created_at, id);
create index idx_account_audit_trace on account_operation_logs (trace_id);
-- 用户导入暂存表，仅管理员本人可访问
create table account_user_imports (
    id varchar(64) primary key comment '导入批次唯一标识 UUID', -- 导入批次唯一标识 UUID
    file_hash varchar(64) not null comment '原始文件 SHA-256 摘要', -- 原始文件 SHA-256 摘要
    row_count integer not null comment '数据行数量，上限一千', -- 数据行数量，上限一千
    valid boolean not null comment '预览是否通过全部校验', -- 预览是否通过全部校验
    rows_json longtext comment '临时用户字段和错误码，提交或过期后清除', -- 临时用户字段和错误码，提交或过期后清除
    result_json longtext comment '成功结果，仅保存行号和用户标识', -- 成功结果，仅保存行号和用户标识
    status varchar(32) not null comment '批次状态 preview 或 committed', -- 批次状态 preview 或 committed
    expires_at timestamp not null comment '预览或结果失效时间 UTC', -- 预览或结果失效时间 UTC
    created_by varchar(64) not null comment '创建批次的管理员用户标识', -- 创建批次的管理员用户标识
    updated_by varchar(64) comment '最后操作管理员标识', -- 最后操作管理员标识
    created_at timestamp not null default current_timestamp comment '创建时间 UTC', -- 创建时间 UTC
    updated_at timestamp null comment '最后更新时间 UTC', -- 最后更新时间 UTC
    constraint ck_user_import_status check (status in ('preview', 'committed')),
    constraint ck_user_import_count check (row_count between 1 and 1000)
) engine=InnoDB default charset=utf8mb4 comment='用户导入暂存表，仅管理员本人可访问';

create index idx_user_import_expires on account_user_imports (expires_at);
