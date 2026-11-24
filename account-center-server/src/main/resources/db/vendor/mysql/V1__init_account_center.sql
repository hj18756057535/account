-- 全新开发阶段的完整初始化结构；仅对空库执行，不包含历史兼容或回填。

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
    created_by varchar(64) comment '创建人；system 表示系统操作', -- 创建人；system 表示系统操作
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
    outcome varchar(16) not null default 'unknown' comment '结果：success 成功、failure 失败、unknown 历史未记录', -- 结果：success 成功、failure 失败、unknown 历史未记录
    created_by varchar(64) comment '创建人；system 表示系统操作', -- 创建人；system 表示系统操作
    updated_by varchar(64) comment '更新人；system 表示系统操作', -- 更新人；system 表示系统操作
    updated_at timestamp comment '更新时间，UTC' -- 更新时间，UTC
) engine=InnoDB default charset=utf8mb4 comment='操作审计日志';

-- HMAC Nonce 防重放记录
create table account_nonce_records (
    nonce varchar(128) primary key comment '一次性随机数', -- 一次性随机数
    app_code varchar(64) not null comment '所属应用编码', -- 所属应用编码
    purpose varchar(64) not null comment '用途标识', -- 用途标识
    expires_at timestamp not null comment '过期时间', -- 过期时间
    created_at timestamp not null default current_timestamp comment '创建时间', -- 创建时间
    created_by varchar(64) comment '创建人；system 表示系统操作', -- 创建人；system 表示系统操作
    updated_by varchar(64) comment '更新人；system 表示系统操作', -- 更新人；system 表示系统操作
    updated_at timestamp comment '更新时间，UTC' -- 更新时间，UTC
) engine=InnoDB default charset=utf8mb4 comment='HMAC Nonce 防重放记录';

-- 管理端一次性票据
create table account_admin_tickets (
    code varchar(128) primary key comment 'ticket 编码', -- ticket 编码
    app_code varchar(64) not null comment '目标应用编码', -- 目标应用编码
    user_id varchar(64) not null comment '关联用户 ID', -- 关联用户 ID
    purpose varchar(64) not null comment '用途：iframe-permission 等', -- 用途：iframe-permission 等
    expires_at timestamp not null comment '票据过期时间；有效期由服务端配置决定', -- 票据过期时间；有效期由服务端配置决定
    used_at timestamp null comment '使用时间（一次性，使用后标记）', -- 使用时间（一次性，使用后标记）
    created_at timestamp not null default current_timestamp comment '创建时间', -- 创建时间
    created_by varchar(64) comment '创建人；system 表示系统操作', -- 创建人；system 表示系统操作
    updated_by varchar(64) comment '更新人；system 表示系统操作', -- 更新人；system 表示系统操作
    updated_at timestamp comment '更新时间，UTC' -- 更新时间，UTC
) engine=InnoDB default charset=utf8mb4 comment='管理端一次性票据';

-- 管理角色分配
create table account_admin_roles (
    user_id varchar(64) not null comment '用户 ID', -- 用户 ID
    role_code varchar(64) not null comment '角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR', -- 角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR
    created_at timestamp not null default current_timestamp comment '分配时间', -- 分配时间
    created_by varchar(64) comment '创建人；system 表示系统操作', -- 创建人；system 表示系统操作
    updated_by varchar(64) comment '更新人；system 表示系统操作', -- 更新人；system 表示系统操作
    updated_at timestamp comment '更新时间，UTC', -- 更新时间，UTC
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
    created_by varchar(64) comment '创建人；system 表示系统操作', -- 创建人；system 表示系统操作
    updated_by varchar(64) comment '更新人；system 表示系统操作', -- 更新人；system 表示系统操作
    updated_at timestamp comment '更新时间，UTC', -- 更新时间，UTC
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
    payload_json longtext comment '冻结的用户同步请求，终态或保留到期后清除', -- 冻结的用户同步请求，终态或保留到期后清除
    payload_hash varchar(64) comment '冻结请求 SHA-256 摘要', -- 冻结请求 SHA-256 摘要
    operator_id varchar(64) comment '发起当前命令的管理员标识', -- 发起当前命令的管理员标识
    target_application_version bigint comment '冻结目标应用配置版本', -- 冻结目标应用配置版本
    lease_token varchar(64) comment '当前处理租约随机标识，防止过时提交', -- 当前处理租约随机标识，防止过时提交
    lease_expires_at timestamp null comment '处理租约到期时间 UTC', -- 处理租约到期时间 UTC
    created_by varchar(64) comment '创建人；system 表示系统操作', -- 创建人；system 表示系统操作
    updated_by varchar(64) comment '更新人；system 表示系统操作', -- 更新人；system 表示系统操作
    constraint uk_account_sync_command_idempotency unique (idempotency_key),
    constraint fk_sync_command_user foreign key (user_id) references account_users (id),
    constraint fk_sync_command_app foreign key (app_code) references account_applications (app_code)
) engine=InnoDB default charset=utf8mb4 comment='应用准入同步命令';

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

-- OIDC授权记录，载荷含敏感凭据
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

-- 应用用户角色组织目录导入记录
create table account_directory_imports (
    id varchar(64) primary key comment '目录导入记录 UUID', -- 目录导入记录 UUID
    app_code varchar(64) not null comment '来源应用编码', -- 来源应用编码
    operator_id varchar(64) not null comment '执行预览和提交的管理员', -- 执行预览和提交的管理员
    application_version bigint not null comment '预览时的应用版本', -- 预览时的应用版本
    status varchar(16) not null comment '状态 preview 或 committed', -- 状态 preview 或 committed
    snapshot_json longtext not null comment '用户角色组织来源快照，不包含密码令牌', -- 用户角色组织来源快照，不包含密码令牌
    result_json longtext comment '不含凭据的导入结果', -- 不含凭据的导入结果
    created_at timestamp not null comment '预览创建时间', -- 预览创建时间
    expires_at timestamp not null comment '未提交预览的过期时间', -- 未提交预览的过期时间
    committed_at timestamp comment '完成导入的时间', -- 完成导入的时间
    created_by varchar(64) comment '创建人；system 表示系统操作', -- 创建人；system 表示系统操作
    updated_by varchar(64) comment '更新人；system 表示系统操作', -- 更新人；system 表示系统操作
    updated_at timestamp comment '更新时间，UTC' -- 更新时间，UTC
) engine=InnoDB default charset=utf8mb4 comment='应用用户角色组织目录导入记录';

-- 应用来源本地用户与全局身份关联
create table account_application_user_links (
    app_code varchar(64) not null comment '来源应用编码', -- 来源应用编码
    user_id varchar(64) not null comment 'Account 全局用户 ID', -- Account 全局用户 ID
    tenant_code varchar(64) not null comment '来源固定租户编码', -- 来源固定租户编码
    local_user_id varchar(255) not null comment '来源本地用户 ID，保留原精度', -- 来源本地用户 ID，保留原精度
    created_by varchar(64) not null comment '确认关联的管理员', -- 确认关联的管理员
    created_at timestamp not null comment '首次确认关联时间', -- 首次确认关联时间
    updated_by varchar(64) comment '更新人；system 表示系统操作', -- 更新人；system 表示系统操作
    updated_at timestamp comment '更新时间，UTC', -- 更新时间，UTC
    primary key (app_code, user_id),
    constraint uk_account_user_link_local unique (app_code, tenant_code, local_user_id)
) engine=InnoDB default charset=utf8mb4 comment='应用来源本地用户与全局身份关联';

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
