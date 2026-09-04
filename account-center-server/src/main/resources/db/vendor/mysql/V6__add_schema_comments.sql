-- V6：仅补充表和字段注释，列属性以 V1～V5 为基线，不改变主外键或索引。
-- MySQL 8.0 使用；要求未手工偏离版本迁移结构。显式 INPLACE/LOCK=NONE 禁止退化为复制表。
-- DDL 非事务性：若失败先检查 Flyway 历史和已应用注释，不盲目 repair 或删表。

-- 全局用户
ALTER TABLE account_users
    MODIFY COLUMN id varchar(64) not null COMMENT '用户唯一标识（UUID）', -- 用户唯一标识（UUID）
    MODIFY COLUMN account varchar(128) not null COMMENT '登录账号，全局唯一', -- 登录账号，全局唯一
    MODIFY COLUMN email varchar(255) not null COMMENT '邮箱地址', -- 邮箱地址
    MODIFY COLUMN name varchar(128) not null COMMENT '用户姓名', -- 用户姓名
    MODIFY COLUMN phone varchar(64) not null COMMENT '手机号码', -- 手机号码
    MODIFY COLUMN status varchar(32) not null default 'enabled' COMMENT '状态：enabled-启用 disabled-禁用', -- 状态：enabled-启用 disabled-禁用
    MODIFY COLUMN created_by varchar(64) COMMENT '创建人', -- 创建人
    MODIFY COLUMN updated_by varchar(64) COMMENT '最后修改人', -- 最后修改人
    MODIFY COLUMN created_at timestamp not null default current_timestamp COMMENT '创建时间', -- 创建时间
    MODIFY COLUMN updated_at timestamp COMMENT '最后修改时间', -- 最后修改时间
    MODIFY COLUMN password varchar(255) COMMENT '登录密码 BCrypt 哈希；允许为空，禁止保存明文', -- 登录密码 BCrypt 哈希；允许为空，禁止保存明文
    MODIFY COLUMN version bigint not null default 1 COMMENT '资源乐观锁版本；初始为 1，每次更新递增', -- 资源乐观锁版本；初始为 1，每次更新递增
    COMMENT = '全局用户',
    ALGORITHM = INPLACE, LOCK = NONE;

-- 应用登记
ALTER TABLE account_applications
    MODIFY COLUMN app_code varchar(64) not null COMMENT '应用编码（业务主键）', -- 应用编码（业务主键）
    MODIFY COLUMN name varchar(128) not null COMMENT '应用名称', -- 应用名称
    MODIFY COLUMN entry_url varchar(512) not null COMMENT '应用入口地址', -- 应用入口地址
    MODIFY COLUMN sso_callback_url varchar(512) not null COMMENT 'SSO 登录回调地址', -- SSO 登录回调地址
    MODIFY COLUMN permission_iframe_url varchar(512) not null COMMENT 'iframe 授权页面地址（含 {externalUserId} 占位符）', -- iframe 授权页面地址（含 {externalUserId} 占位符）
    MODIFY COLUMN notify_base_url varchar(512) not null COMMENT '用户同步通知基地址', -- 用户同步通知基地址
    MODIFY COLUMN secret varchar(255) not null COMMENT '受保护的 HMAC 签名密钥；不得进入普通响应、日志或文档示例', -- 受保护的 HMAC 签名密钥；不得进入普通响应、日志或文档示例
    MODIFY COLUMN default_tenant_code varchar(64) not null default 'default' COMMENT '默认租户编码', -- 默认租户编码
    MODIFY COLUMN status varchar(32) not null default 'enabled' COMMENT '状态：enabled-启用 disabled-禁用', -- 状态：enabled-启用 disabled-禁用
    MODIFY COLUMN secret_version integer not null default 1 COMMENT '密钥版本号，轮换时递增', -- 密钥版本号，轮换时递增
    MODIFY COLUMN created_by varchar(64) COMMENT '创建人', -- 创建人
    MODIFY COLUMN updated_by varchar(64) COMMENT '最后修改人', -- 最后修改人
    MODIFY COLUMN created_at timestamp not null default current_timestamp COMMENT '创建时间', -- 创建时间
    MODIFY COLUMN updated_at timestamp COMMENT '最后修改时间', -- 最后修改时间
    MODIFY COLUMN version bigint not null default 1 COMMENT '资源乐观锁版本；初始为 1，每次更新递增', -- 资源乐观锁版本；初始为 1，每次更新递增
    MODIFY COLUMN protocol_capabilities varchar(255) not null default 'sso,admin_ticket,user_sync' COMMENT '协议能力编码列表，逗号分隔：sso、admin_ticket、user_sync', -- 协议能力编码列表，逗号分隔：sso、admin_ticket、user_sync
    MODIFY COLUMN secret_state varchar(32) not null default 'active' COMMENT '密钥状态：active 可用、revoked 已撤销', -- 密钥状态：active 可用、revoked 已撤销
    COMMENT = '应用登记',
    ALGORITHM = INPLACE, LOCK = NONE;

-- 用户应用准入
ALTER TABLE account_user_applications
    MODIFY COLUMN user_id varchar(64) not null COMMENT '用户 ID（关联 account_users.id）', -- 用户 ID（关联 account_users.id）
    MODIFY COLUMN app_code varchar(64) not null COMMENT '应用编码（关联 account_applications.app_code）', -- 应用编码（关联 account_applications.app_code）
    MODIFY COLUMN status varchar(32) not null default 'enabled' COMMENT '授权状态：enabled-有效 disabled-已撤销', -- 授权状态：enabled-有效 disabled-已撤销
    MODIFY COLUMN authorized_by varchar(64) COMMENT '授权操作人', -- 授权操作人
    MODIFY COLUMN created_at timestamp not null default current_timestamp COMMENT '授权时间', -- 授权时间
    MODIFY COLUMN deauthorized_at timestamp COMMENT '取消授权时间', -- 取消授权时间
    MODIFY COLUMN desired_status varchar(32) not null default 'enabled' COMMENT 'Account 期望准入状态：enabled / disabled；不代表业务应用已同步', -- Account 期望准入状态：enabled / disabled；不代表业务应用已同步
    MODIFY COLUMN version bigint not null default 1 COMMENT '资源乐观锁版本；初始为 1，每次更新递增', -- 资源乐观锁版本；初始为 1，每次更新递增
    MODIFY COLUMN updated_by varchar(64) COMMENT '最后修改人 ID', -- 最后修改人 ID
    MODIFY COLUMN updated_at timestamp COMMENT '最后修改时间', -- 最后修改时间
    COMMENT = '用户应用准入',
    ALGORITHM = INPLACE, LOCK = NONE;

-- 操作审计日志
ALTER TABLE account_operation_logs
    MODIFY COLUMN id varchar(64) not null COMMENT '日志唯一标识', -- 日志唯一标识
    MODIFY COLUMN operator_id varchar(64) COMMENT '操作人 ID', -- 操作人 ID
    MODIFY COLUMN operation_type varchar(64) not null COMMENT '稳定操作编码，如 USER_CREATED / APPLICATION_ACCESS_CHANGED；不随展示语言变化', -- 稳定操作编码，如 USER_CREATED / APPLICATION_ACCESS_CHANGED；不随展示语言变化
    MODIFY COLUMN target_type varchar(64) not null COMMENT '操作对象类型：USER/APPLICATION/SSO_TICKET 等', -- 操作对象类型：USER/APPLICATION/SSO_TICKET 等
    MODIFY COLUMN target_id varchar(128) not null COMMENT '操作对象 ID', -- 操作对象 ID
    MODIFY COLUMN detail text COMMENT '脱敏操作摘要 JSON；禁止保存密码、Secret、票据或个人敏感值', -- 脱敏操作摘要 JSON；禁止保存密码、Secret、票据或个人敏感值
    MODIFY COLUMN created_at timestamp not null default current_timestamp COMMENT '操作时间', -- 操作时间
    MODIFY COLUMN trace_id varchar(64) COMMENT '服务端请求关联 ID；历史记录或非请求调用允许为空', -- 服务端请求关联 ID；历史记录或非请求调用允许为空
    MODIFY COLUMN outcome varchar(16) not null default 'unknown' COMMENT '结果：success 成功、failure 失败、unknown 历史未记录', -- 结果：success 成功、failure 失败、unknown 历史未记录
    COMMENT = '操作审计日志',
    ALGORITHM = INPLACE, LOCK = NONE;

-- HMAC Nonce 防重放记录
ALTER TABLE account_nonce_records
    MODIFY COLUMN nonce varchar(128) not null COMMENT '一次性随机数', -- 一次性随机数
    MODIFY COLUMN app_code varchar(64) not null COMMENT '所属应用编码', -- 所属应用编码
    MODIFY COLUMN purpose varchar(64) not null COMMENT '用途标识', -- 用途标识
    MODIFY COLUMN expires_at timestamp not null COMMENT '过期时间', -- 过期时间
    MODIFY COLUMN created_at timestamp not null default current_timestamp COMMENT '创建时间', -- 创建时间
    COMMENT = 'HMAC Nonce 防重放记录',
    ALGORITHM = INPLACE, LOCK = NONE;

-- 管理端一次性票据
ALTER TABLE account_admin_tickets
    MODIFY COLUMN code varchar(128) not null COMMENT 'ticket 编码', -- ticket 编码
    MODIFY COLUMN app_code varchar(64) not null COMMENT '目标应用编码', -- 目标应用编码
    MODIFY COLUMN user_id varchar(64) not null COMMENT '关联用户 ID', -- 关联用户 ID
    MODIFY COLUMN purpose varchar(64) not null COMMENT '用途：iframe-permission 等', -- 用途：iframe-permission 等
    MODIFY COLUMN expires_at timestamp not null COMMENT '票据过期时间；有效期由服务端配置决定', -- 票据过期时间；有效期由服务端配置决定
    MODIFY COLUMN used_at timestamp COMMENT '使用时间（一次性，使用后标记）', -- 使用时间（一次性，使用后标记）
    MODIFY COLUMN created_at timestamp not null default current_timestamp COMMENT '创建时间', -- 创建时间
    COMMENT = '管理端一次性票据',
    ALGORITHM = INPLACE, LOCK = NONE;

-- 管理角色分配
ALTER TABLE account_admin_roles
    MODIFY COLUMN user_id varchar(64) not null COMMENT '用户 ID', -- 用户 ID
    MODIFY COLUMN role_code varchar(64) not null COMMENT '角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR', -- 角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR
    MODIFY COLUMN created_at timestamp not null default current_timestamp COMMENT '分配时间', -- 分配时间
    COMMENT = '管理角色分配',
    ALGORITHM = INPLACE, LOCK = NONE;

-- 管理写请求幂等记录
ALTER TABLE account_idempotency_records
    MODIFY COLUMN id varchar(64) not null COMMENT '记录唯一标识', -- 记录唯一标识
    MODIFY COLUMN idempotency_key varchar(128) not null COMMENT '客户端幂等键；与操作者、HTTP 方法和路径共同限定唯一请求', -- 客户端幂等键；与操作者、HTTP 方法和路径共同限定唯一请求
    MODIFY COLUMN operator_id varchar(64) not null COMMENT '操作人用户 ID', -- 操作人用户 ID
    MODIFY COLUMN request_method varchar(16) not null COMMENT '请求 HTTP 方法', -- 请求 HTTP 方法
    MODIFY COLUMN request_path varchar(512) not null COMMENT '规范化资源路径', -- 规范化资源路径
    MODIFY COLUMN request_hash varchar(64) not null COMMENT '请求内容摘要；拒绝幂等键相同而内容不同的请求', -- 请求内容摘要；拒绝幂等键相同而内容不同的请求
    MODIFY COLUMN status varchar(32) not null COMMENT '幂等状态：processing 处理中、completed 已完成', -- 幂等状态：processing 处理中、completed 已完成
    MODIFY COLUMN response_status integer COMMENT '完成后的 HTTP 状态码；处理中为空', -- 完成后的 HTTP 状态码；处理中为空
    MODIFY COLUMN response_body text COMMENT '可重放响应 JSON；敏感结果不持久化，保存空 JSON 对象', -- 可重放响应 JSON；敏感结果不持久化，保存空 JSON 对象
    MODIFY COLUMN created_at timestamp not null default current_timestamp COMMENT '创建时间', -- 创建时间
    MODIFY COLUMN expires_at timestamp not null COMMENT '过期时间；保留期限由服务端定义', -- 过期时间；保留期限由服务端定义
    COMMENT = '管理写请求幂等记录',
    ALGORITHM = INPLACE, LOCK = NONE;

-- 应用准入同步命令
ALTER TABLE account_sync_commands
    MODIFY COLUMN id varchar(64) not null COMMENT '记录唯一标识', -- 记录唯一标识
    MODIFY COLUMN idempotency_key varchar(128) not null COMMENT '命令级幂等键，全局唯一', -- 命令级幂等键，全局唯一
    MODIFY COLUMN user_id varchar(64) not null COMMENT '目标用户 ID，关联 account_users.id', -- 目标用户 ID，关联 account_users.id
    MODIFY COLUMN app_code varchar(64) not null COMMENT '目标应用编码，关联 account_applications.app_code', -- 目标应用编码，关联 account_applications.app_code
    MODIFY COLUMN desired_status varchar(32) not null COMMENT 'Account 期望准入状态：enabled / disabled；不代表业务应用已同步', -- Account 期望准入状态：enabled / disabled；不代表业务应用已同步
    MODIFY COLUMN sync_version bigint not null COMMENT '命令对应的准入版本，用于幂等和乱序保护', -- 命令对应的准入版本，用于幂等和乱序保护
    MODIFY COLUMN status varchar(32) not null COMMENT '命令状态；当前为 pending_application_adaptation，尚未实际投递', -- 命令状态；当前为 pending_application_adaptation，尚未实际投递
    MODIFY COLUMN attempts integer not null default 0 COMMENT '实际投递尝试次数，初始为 0', -- 实际投递尝试次数，初始为 0
    MODIFY COLUMN next_retry_at timestamp COMMENT '下次重试时间；尚未安排时为空', -- 下次重试时间；尚未安排时为空
    MODIFY COLUMN error_code varchar(128) COMMENT '最近失败的稳定错误码；禁止保存敏感错误详情', -- 最近失败的稳定错误码；禁止保存敏感错误详情
    MODIFY COLUMN trace_id varchar(64) not null COMMENT '服务端关联 ID；用于请求或命令追踪', -- 服务端关联 ID；用于请求或命令追踪
    MODIFY COLUMN created_at timestamp not null default current_timestamp COMMENT '创建时间', -- 创建时间
    MODIFY COLUMN updated_at timestamp COMMENT '最后修改时间', -- 最后修改时间
    COMMENT = '应用准入同步命令',
    ALGORITHM = INPLACE, LOCK = NONE;
