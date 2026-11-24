-- 全新开发阶段的完整初始化结构；仅对空库执行，不包含历史兼容或回填。

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
comment on table account_users is '全局用户';
comment on column account_users.id is '用户唯一标识（UUID）';
comment on column account_users.account is '登录账号，全局唯一';
comment on column account_users.email is '邮箱地址';
comment on column account_users.name is '用户姓名';
comment on column account_users.phone is '手机号码';
comment on column account_users.status is '状态：enabled-启用 disabled-禁用';
comment on column account_users.created_by is '创建人';
comment on column account_users.updated_by is '最后修改人';
comment on column account_users.created_at is '创建时间';
comment on column account_users.updated_at is '最后修改时间';
comment on column account_users.password is '登录密码 BCrypt 哈希；允许为空，禁止保存明文';
comment on column account_users.version is '资源乐观锁版本；初始为 1，每次更新递增';

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
    secret_state varchar(32) not null default 'active' -- 密钥状态：active 可用、revoked 已撤销
);
comment on table account_applications is '应用登记';
comment on column account_applications.app_code is '应用编码（业务主键）';
comment on column account_applications.name is '应用名称';
comment on column account_applications.entry_url is '应用入口地址';
comment on column account_applications.sso_callback_url is 'SSO 登录回调地址';
comment on column account_applications.permission_iframe_url is 'iframe 授权页面地址（含 {externalUserId} 占位符）';
comment on column account_applications.notify_base_url is '用户同步通知基地址';
comment on column account_applications.secret is '受保护的 HMAC 签名密钥；不得进入普通响应、日志或文档示例';
comment on column account_applications.default_tenant_code is '默认租户编码';
comment on column account_applications.status is '状态：enabled-启用 disabled-禁用';
comment on column account_applications.secret_version is '密钥版本号，轮换时递增';
comment on column account_applications.created_by is '创建人';
comment on column account_applications.updated_by is '最后修改人';
comment on column account_applications.created_at is '创建时间';
comment on column account_applications.updated_at is '最后修改时间';
comment on column account_applications.version is '资源乐观锁版本；初始为 1，每次更新递增';
comment on column account_applications.secret_state is '密钥状态：active 可用、revoked 已撤销';

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
    created_by varchar(64), -- 创建人；system 表示系统操作
    primary key (user_id, app_code),
    constraint fk_ua_user foreign key (user_id) references account_users (id),
    constraint fk_ua_app foreign key (app_code) references account_applications (app_code)
);
comment on table account_user_applications is '用户应用准入';
comment on column account_user_applications.user_id is '用户 ID（关联 account_users.id）';
comment on column account_user_applications.app_code is '应用编码（关联 account_applications.app_code）';
comment on column account_user_applications.status is '授权状态：enabled-有效 disabled-已撤销';
comment on column account_user_applications.authorized_by is '授权操作人';
comment on column account_user_applications.created_at is '授权时间';
comment on column account_user_applications.deauthorized_at is '取消授权时间';
comment on column account_user_applications.desired_status is 'Account 期望准入状态：enabled / disabled；不代表业务应用已同步';
comment on column account_user_applications.version is '资源乐观锁版本；初始为 1，每次更新递增';
comment on column account_user_applications.updated_by is '最后修改人 ID';
comment on column account_user_applications.updated_at is '最后修改时间';
comment on column account_user_applications.created_by is '创建人；system 表示系统操作';

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
    outcome varchar(16) not null default 'unknown', -- 结果：success 成功、failure 失败、unknown 历史未记录
    created_by varchar(64), -- 创建人；system 表示系统操作
    updated_by varchar(64), -- 更新人；system 表示系统操作
    updated_at timestamp -- 更新时间，UTC
);
comment on table account_operation_logs is '操作审计日志';
comment on column account_operation_logs.id is '日志唯一标识';
comment on column account_operation_logs.operator_id is '操作人 ID';
comment on column account_operation_logs.operation_type is '稳定操作编码，如 USER_CREATED / APPLICATION_ACCESS_CHANGED；不随展示语言变化';
comment on column account_operation_logs.target_type is '操作对象类型：USER/APPLICATION/SSO_TICKET 等';
comment on column account_operation_logs.target_id is '操作对象 ID';
comment on column account_operation_logs.detail is '脱敏操作摘要 JSON；禁止保存密码、Secret、票据或个人敏感值';
comment on column account_operation_logs.created_at is '操作时间';
comment on column account_operation_logs.trace_id is '服务端请求关联 ID；历史记录或非请求调用允许为空';
comment on column account_operation_logs.outcome is '结果：success 成功、failure 失败、unknown 历史未记录';
comment on column account_operation_logs.created_by is '创建人；system 表示系统操作';
comment on column account_operation_logs.updated_by is '更新人；system 表示系统操作';
comment on column account_operation_logs.updated_at is '更新时间，UTC';

-- HMAC Nonce 防重放记录
create table account_nonce_records (
    nonce varchar(128) primary key, -- 一次性随机数
    app_code varchar(64) not null, -- 所属应用编码
    purpose varchar(64) not null, -- 用途标识
    expires_at timestamp not null, -- 过期时间
    created_at timestamp not null default current_timestamp, -- 创建时间
    created_by varchar(64), -- 创建人；system 表示系统操作
    updated_by varchar(64), -- 更新人；system 表示系统操作
    updated_at timestamp -- 更新时间，UTC
);
comment on table account_nonce_records is 'HMAC Nonce 防重放记录';
comment on column account_nonce_records.nonce is '一次性随机数';
comment on column account_nonce_records.app_code is '所属应用编码';
comment on column account_nonce_records.purpose is '用途标识';
comment on column account_nonce_records.expires_at is '过期时间';
comment on column account_nonce_records.created_at is '创建时间';
comment on column account_nonce_records.created_by is '创建人；system 表示系统操作';
comment on column account_nonce_records.updated_by is '更新人；system 表示系统操作';
comment on column account_nonce_records.updated_at is '更新时间，UTC';

-- 管理端一次性票据
create table account_admin_tickets (
    code varchar(128) primary key, -- ticket 编码
    app_code varchar(64) not null, -- 目标应用编码
    user_id varchar(64) not null, -- 关联用户 ID
    purpose varchar(64) not null, -- 用途：iframe-permission 等
    expires_at timestamp not null, -- 票据过期时间；有效期由服务端配置决定
    used_at timestamp, -- 使用时间（一次性，使用后标记）
    created_at timestamp not null default current_timestamp, -- 创建时间
    created_by varchar(64), -- 创建人；system 表示系统操作
    updated_by varchar(64), -- 更新人；system 表示系统操作
    updated_at timestamp -- 更新时间，UTC
);
comment on table account_admin_tickets is '管理端一次性票据';
comment on column account_admin_tickets.code is 'ticket 编码';
comment on column account_admin_tickets.app_code is '目标应用编码';
comment on column account_admin_tickets.user_id is '关联用户 ID';
comment on column account_admin_tickets.purpose is '用途：iframe-permission 等';
comment on column account_admin_tickets.expires_at is '票据过期时间；有效期由服务端配置决定';
comment on column account_admin_tickets.used_at is '使用时间（一次性，使用后标记）';
comment on column account_admin_tickets.created_at is '创建时间';
comment on column account_admin_tickets.created_by is '创建人；system 表示系统操作';
comment on column account_admin_tickets.updated_by is '更新人；system 表示系统操作';
comment on column account_admin_tickets.updated_at is '更新时间，UTC';

-- 管理角色分配
create table account_admin_roles (
    user_id varchar(64) not null, -- 用户 ID
    role_code varchar(64) not null, -- 角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR
    created_at timestamp not null default current_timestamp, -- 分配时间
    created_by varchar(64), -- 创建人；system 表示系统操作
    updated_by varchar(64), -- 更新人；system 表示系统操作
    updated_at timestamp, -- 更新时间，UTC
    primary key (user_id, role_code)
);
comment on table account_admin_roles is '管理角色分配';
comment on column account_admin_roles.user_id is '用户 ID';
comment on column account_admin_roles.role_code is '角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR';
comment on column account_admin_roles.created_at is '分配时间';
comment on column account_admin_roles.created_by is '创建人；system 表示系统操作';
comment on column account_admin_roles.updated_by is '更新人；system 表示系统操作';
comment on column account_admin_roles.updated_at is '更新时间，UTC';

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
    created_by varchar(64), -- 创建人；system 表示系统操作
    updated_by varchar(64), -- 更新人；system 表示系统操作
    updated_at timestamp, -- 更新时间，UTC
    constraint uk_account_idempotency_scope unique (operator_id, request_method, request_path, idempotency_key)
);
comment on table account_idempotency_records is '管理写请求幂等记录';
comment on column account_idempotency_records.id is '记录唯一标识';
comment on column account_idempotency_records.idempotency_key is '客户端幂等键；与操作者、HTTP 方法和路径共同限定唯一请求';
comment on column account_idempotency_records.operator_id is '操作人用户 ID';
comment on column account_idempotency_records.request_method is '请求 HTTP 方法';
comment on column account_idempotency_records.request_path is '规范化资源路径';
comment on column account_idempotency_records.request_hash is '请求内容摘要；拒绝幂等键相同而内容不同的请求';
comment on column account_idempotency_records.status is '幂等状态：processing 处理中、completed 已完成';
comment on column account_idempotency_records.response_status is '完成后的 HTTP 状态码；处理中为空';
comment on column account_idempotency_records.response_body is '可重放响应 JSON；敏感结果不持久化，保存空 JSON 对象';
comment on column account_idempotency_records.created_at is '创建时间';
comment on column account_idempotency_records.expires_at is '过期时间；保留期限由服务端定义';
comment on column account_idempotency_records.created_by is '创建人；system 表示系统操作';
comment on column account_idempotency_records.updated_by is '更新人；system 表示系统操作';
comment on column account_idempotency_records.updated_at is '更新时间，UTC';

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
    payload_json text, -- 冻结的用户同步请求，终态或保留到期后清除
    payload_hash varchar(64), -- 冻结请求 SHA-256 摘要
    operator_id varchar(64), -- 发起当前命令的管理员标识
    target_application_version bigint, -- 冻结目标应用配置版本
    lease_token varchar(64), -- 当前处理租约随机标识，防止过时提交
    lease_expires_at timestamp, -- 处理租约到期时间 UTC
    created_by varchar(64), -- 创建人；system 表示系统操作
    updated_by varchar(64), -- 更新人；system 表示系统操作
    constraint uk_account_sync_command_idempotency unique (idempotency_key),
    constraint fk_sync_command_user foreign key (user_id) references account_users (id),
    constraint fk_sync_command_app foreign key (app_code) references account_applications (app_code)
);
comment on table account_sync_commands is '应用准入同步命令';
comment on column account_sync_commands.id is '记录唯一标识';
comment on column account_sync_commands.idempotency_key is '命令级幂等键，全局唯一';
comment on column account_sync_commands.user_id is '目标用户 ID，关联 account_users.id';
comment on column account_sync_commands.app_code is '目标应用编码，关联 account_applications.app_code';
comment on column account_sync_commands.desired_status is 'Account 期望准入状态：enabled / disabled；不代表业务应用已同步';
comment on column account_sync_commands.sync_version is '命令对应的准入版本，用于幂等和乱序保护';
comment on column account_sync_commands.status is '命令状态；当前为 pending_application_adaptation，尚未实际投递';
comment on column account_sync_commands.attempts is '实际投递尝试次数，初始为 0';
comment on column account_sync_commands.next_retry_at is '下次重试时间；尚未安排时为空';
comment on column account_sync_commands.error_code is '最近失败的稳定错误码；禁止保存敏感错误详情';
comment on column account_sync_commands.trace_id is '服务端关联 ID；用于请求或命令追踪';
comment on column account_sync_commands.created_at is '创建时间';
comment on column account_sync_commands.updated_at is '最后修改时间';
comment on column account_sync_commands.payload_json is '冻结的用户同步请求，终态或保留到期后清除';
comment on column account_sync_commands.payload_hash is '冻结请求 SHA-256 摘要';
comment on column account_sync_commands.operator_id is '发起当前命令的管理员标识';
comment on column account_sync_commands.target_application_version is '冻结目标应用配置版本';
comment on column account_sync_commands.lease_token is '当前处理租约随机标识，防止过时提交';
comment on column account_sync_commands.lease_expires_at is '处理租约到期时间 UTC';
comment on column account_sync_commands.created_by is '创建人；system 表示系统操作';
comment on column account_sync_commands.updated_by is '更新人；system 表示系统操作';

-- 用户导入暂存表，仅管理员本人可访问
create table account_user_imports (
    id varchar(64) primary key, -- 导入批次唯一标识 UUID
    file_hash varchar(64) not null, -- 原始文件 SHA-256 摘要
    row_count integer not null, -- 数据行数量，上限一千
    valid boolean not null, -- 预览是否通过全部校验
    rows_json text, -- 临时用户字段和错误码，提交或过期后清除
    result_json text, -- 成功结果，仅保存行号和用户标识
    status varchar(32) not null, -- 批次状态 preview 或 committed
    expires_at timestamp not null, -- 预览或结果失效时间 UTC
    created_by varchar(64) not null, -- 创建批次的管理员用户标识
    updated_by varchar(64), -- 最后操作管理员标识
    created_at timestamp not null default current_timestamp, -- 创建时间 UTC
    updated_at timestamp, -- 最后更新时间 UTC
    constraint ck_user_import_status check (status in ('preview', 'committed')),
    constraint ck_user_import_count check (row_count between 1 and 1000)
);
comment on table account_user_imports is '用户导入暂存表，仅管理员本人可访问';
comment on column account_user_imports.id is '导入批次唯一标识 UUID';
comment on column account_user_imports.file_hash is '原始文件 SHA-256 摘要';
comment on column account_user_imports.row_count is '数据行数量，上限一千';
comment on column account_user_imports.valid is '预览是否通过全部校验';
comment on column account_user_imports.rows_json is '临时用户字段和错误码，提交或过期后清除';
comment on column account_user_imports.result_json is '成功结果，仅保存行号和用户标识';
comment on column account_user_imports.status is '批次状态 preview 或 committed';
comment on column account_user_imports.expires_at is '预览或结果失效时间 UTC';
comment on column account_user_imports.created_by is '创建批次的管理员用户标识';
comment on column account_user_imports.updated_by is '最后操作管理员标识';
comment on column account_user_imports.created_at is '创建时间 UTC';
comment on column account_user_imports.updated_at is '最后更新时间 UTC';

-- 应用用户映射与可靠准入确认
create table account_application_user_states (
    user_id varchar(64) not null, -- 全局用户标识，关联账号中心用户
    app_code varchar(64) not null, -- 所属应用编码
    local_user_id varchar(255) not null, -- 应用确认的本地用户标识，不按账号猜测
    applied_status varchar(32) not null, -- 已确认准入状态 enabled 或 disabled
    applied_version bigint not null, -- 已确认准入版本，必须匹配投递命令
    last_synced_at timestamp not null, -- 最近确认时间 UTC
    created_by varchar(64), -- 首次确认关联的操作人
    updated_by varchar(64), -- 最近确认关联的操作人
    created_at timestamp not null default current_timestamp, -- 创建时间 UTC
    updated_at timestamp, -- 最近更新时间 UTC
    primary key (user_id, app_code),
    constraint uk_app_local_user unique (app_code, local_user_id),
    constraint fk_app_state_user foreign key (user_id) references account_users (id),
    constraint fk_app_state_app foreign key (app_code) references account_applications (app_code),
    constraint ck_app_state_status check (applied_status in ('enabled', 'disabled')),
    constraint ck_app_state_version check (applied_version > 0)
);
comment on table account_application_user_states is '应用用户映射与可靠准入确认';
comment on column account_application_user_states.user_id is '全局用户标识，关联账号中心用户';
comment on column account_application_user_states.app_code is '所属应用编码';
comment on column account_application_user_states.local_user_id is '应用确认的本地用户标识，不按账号猜测';
comment on column account_application_user_states.applied_status is '已确认准入状态 enabled 或 disabled';
comment on column account_application_user_states.applied_version is '已确认准入版本，必须匹配投递命令';
comment on column account_application_user_states.last_synced_at is '最近确认时间 UTC';
comment on column account_application_user_states.created_by is '首次确认关联的操作人';
comment on column account_application_user_states.updated_by is '最近确认关联的操作人';
comment on column account_application_user_states.created_at is '创建时间 UTC';
comment on column account_application_user_states.updated_at is '最近更新时间 UTC';

-- OIDC授权记录，载荷含敏感凭据
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

-- 应用用户角色组织目录导入记录
create table account_directory_imports (
    id varchar(64) primary key, -- 目录导入记录 UUID
    app_code varchar(64) not null, -- 来源应用编码
    operator_id varchar(64) not null, -- 执行预览和提交的管理员
    application_version bigint not null, -- 预览时的应用版本
    status varchar(16) not null, -- 状态 preview 或 committed
    snapshot_json text not null, -- 用户角色组织来源快照，不包含密码令牌
    result_json text, -- 不含凭据的导入结果
    created_at timestamp not null, -- 预览创建时间
    expires_at timestamp not null, -- 未提交预览的过期时间
    committed_at timestamp, -- 完成导入的时间
    created_by varchar(64), -- 创建人；system 表示系统操作
    updated_by varchar(64), -- 更新人；system 表示系统操作
    updated_at timestamp -- 更新时间，UTC
);
comment on table account_directory_imports is '应用用户角色组织目录导入记录';
comment on column account_directory_imports.id is '目录导入记录 UUID';
comment on column account_directory_imports.app_code is '来源应用编码';
comment on column account_directory_imports.operator_id is '执行预览和提交的管理员';
comment on column account_directory_imports.application_version is '预览时的应用版本';
comment on column account_directory_imports.status is '状态 preview 或 committed';
comment on column account_directory_imports.snapshot_json is '用户角色组织来源快照，不包含密码令牌';
comment on column account_directory_imports.result_json is '不含凭据的导入结果';
comment on column account_directory_imports.created_at is '预览创建时间';
comment on column account_directory_imports.expires_at is '未提交预览的过期时间';
comment on column account_directory_imports.committed_at is '完成导入的时间';
comment on column account_directory_imports.created_by is '创建人；system 表示系统操作';
comment on column account_directory_imports.updated_by is '更新人；system 表示系统操作';
comment on column account_directory_imports.updated_at is '更新时间，UTC';

-- 应用来源本地用户与全局身份关联
create table account_application_user_links (
    app_code varchar(64) not null, -- 来源应用编码
    user_id varchar(64) not null, -- Account 全局用户 ID
    tenant_code varchar(64) not null, -- 来源固定租户编码
    local_user_id varchar(255) not null, -- 来源本地用户 ID，保留原精度
    created_by varchar(64) not null, -- 确认关联的管理员
    created_at timestamp not null, -- 首次确认关联时间
    updated_by varchar(64), -- 更新人；system 表示系统操作
    updated_at timestamp, -- 更新时间，UTC
    primary key (app_code, user_id),
    constraint uk_account_user_link_local unique (app_code, tenant_code, local_user_id)
);
comment on table account_application_user_links is '应用来源本地用户与全局身份关联';
comment on column account_application_user_links.app_code is '来源应用编码';
comment on column account_application_user_links.user_id is 'Account 全局用户 ID';
comment on column account_application_user_links.tenant_code is '来源固定租户编码';
comment on column account_application_user_links.local_user_id is '来源本地用户 ID，保留原精度';
comment on column account_application_user_links.created_by is '确认关联的管理员';
comment on column account_application_user_links.created_at is '首次确认关联时间';
comment on column account_application_user_links.updated_by is '更新人；system 表示系统操作';
comment on column account_application_user_links.updated_at is '更新时间，UTC';

create index idx_account_idempotency_expires on account_idempotency_records (expires_at);
create index idx_account_sync_commands_pending on account_sync_commands (status, created_at);
create index idx_account_audit_page on account_operation_logs (created_at, id);
create index idx_account_audit_trace on account_operation_logs (trace_id);
create index idx_user_import_expires on account_user_imports (expires_at);
create index idx_sync_retry on account_sync_commands (status, next_retry_at);
create index idx_sync_access_version on account_sync_commands (user_id, app_code, sync_version);
create unique index uk_account_oidc_code on account_oidc_authorizations (code_hash);
create unique index uk_account_oidc_access on account_oidc_authorizations (access_token_hash);
create unique index uk_account_oidc_id_token on account_oidc_authorizations (id_token_hash);
create unique index uk_account_oidc_state on account_oidc_authorizations (state_hash);
create index idx_account_directory_latest on account_directory_imports (app_code, status, committed_at);
