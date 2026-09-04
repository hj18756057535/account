-- V6：仅补充表和字段的数据库元数据注释，不修改结构或业务数据。
-- PostgreSQL 与 H2 使用；由应用按数据库类型加载，V1～V5 保持原样。

COMMENT ON TABLE account_users IS '全局用户';
COMMENT ON COLUMN account_users.id IS '用户唯一标识（UUID）';
COMMENT ON COLUMN account_users.account IS '登录账号，全局唯一';
COMMENT ON COLUMN account_users.email IS '邮箱地址';
COMMENT ON COLUMN account_users.name IS '用户姓名';
COMMENT ON COLUMN account_users.phone IS '手机号码';
COMMENT ON COLUMN account_users.status IS '状态：enabled-启用 disabled-禁用';
COMMENT ON COLUMN account_users.created_by IS '创建人';
COMMENT ON COLUMN account_users.updated_by IS '最后修改人';
COMMENT ON COLUMN account_users.created_at IS '创建时间';
COMMENT ON COLUMN account_users.updated_at IS '最后修改时间';
COMMENT ON COLUMN account_users.password IS '登录密码 BCrypt 哈希；允许为空，禁止保存明文';
COMMENT ON COLUMN account_users.version IS '资源乐观锁版本；初始为 1，每次更新递增';

COMMENT ON TABLE account_applications IS '应用登记';
COMMENT ON COLUMN account_applications.app_code IS '应用编码（业务主键）';
COMMENT ON COLUMN account_applications.name IS '应用名称';
COMMENT ON COLUMN account_applications.entry_url IS '应用入口地址';
COMMENT ON COLUMN account_applications.sso_callback_url IS 'SSO 登录回调地址';
COMMENT ON COLUMN account_applications.permission_iframe_url IS 'iframe 授权页面地址（含 {externalUserId} 占位符）';
COMMENT ON COLUMN account_applications.notify_base_url IS '用户同步通知基地址';
COMMENT ON COLUMN account_applications.secret IS '受保护的 HMAC 签名密钥；不得进入普通响应、日志或文档示例';
COMMENT ON COLUMN account_applications.default_tenant_code IS '默认租户编码';
COMMENT ON COLUMN account_applications.status IS '状态：enabled-启用 disabled-禁用';
COMMENT ON COLUMN account_applications.secret_version IS '密钥版本号，轮换时递增';
COMMENT ON COLUMN account_applications.created_by IS '创建人';
COMMENT ON COLUMN account_applications.updated_by IS '最后修改人';
COMMENT ON COLUMN account_applications.created_at IS '创建时间';
COMMENT ON COLUMN account_applications.updated_at IS '最后修改时间';
COMMENT ON COLUMN account_applications.version IS '资源乐观锁版本；初始为 1，每次更新递增';
COMMENT ON COLUMN account_applications.protocol_capabilities IS '协议能力编码列表，逗号分隔：sso、admin_ticket、user_sync';
COMMENT ON COLUMN account_applications.secret_state IS '密钥状态：active 可用、revoked 已撤销';

COMMENT ON TABLE account_user_applications IS '用户应用准入';
COMMENT ON COLUMN account_user_applications.user_id IS '用户 ID（关联 account_users.id）';
COMMENT ON COLUMN account_user_applications.app_code IS '应用编码（关联 account_applications.app_code）';
COMMENT ON COLUMN account_user_applications.status IS '授权状态：enabled-有效 disabled-已撤销';
COMMENT ON COLUMN account_user_applications.authorized_by IS '授权操作人';
COMMENT ON COLUMN account_user_applications.created_at IS '授权时间';
COMMENT ON COLUMN account_user_applications.deauthorized_at IS '取消授权时间';
COMMENT ON COLUMN account_user_applications.desired_status IS 'Account 期望准入状态：enabled / disabled；不代表业务应用已同步';
COMMENT ON COLUMN account_user_applications.version IS '资源乐观锁版本；初始为 1，每次更新递增';
COMMENT ON COLUMN account_user_applications.updated_by IS '最后修改人 ID';
COMMENT ON COLUMN account_user_applications.updated_at IS '最后修改时间';

COMMENT ON TABLE account_operation_logs IS '操作审计日志';
COMMENT ON COLUMN account_operation_logs.id IS '日志唯一标识';
COMMENT ON COLUMN account_operation_logs.operator_id IS '操作人 ID';
COMMENT ON COLUMN account_operation_logs.operation_type IS '稳定操作编码，如 USER_CREATED / APPLICATION_ACCESS_CHANGED；不随展示语言变化';
COMMENT ON COLUMN account_operation_logs.target_type IS '操作对象类型：USER/APPLICATION/SSO_TICKET 等';
COMMENT ON COLUMN account_operation_logs.target_id IS '操作对象 ID';
COMMENT ON COLUMN account_operation_logs.detail IS '脱敏操作摘要 JSON；禁止保存密码、Secret、票据或个人敏感值';
COMMENT ON COLUMN account_operation_logs.created_at IS '操作时间';
COMMENT ON COLUMN account_operation_logs.trace_id IS '服务端请求关联 ID；历史记录或非请求调用允许为空';
COMMENT ON COLUMN account_operation_logs.outcome IS '结果：success 成功、failure 失败、unknown 历史未记录';

COMMENT ON TABLE account_nonce_records IS 'HMAC Nonce 防重放记录';
COMMENT ON COLUMN account_nonce_records.nonce IS '一次性随机数';
COMMENT ON COLUMN account_nonce_records.app_code IS '所属应用编码';
COMMENT ON COLUMN account_nonce_records.purpose IS '用途标识';
COMMENT ON COLUMN account_nonce_records.expires_at IS '过期时间';
COMMENT ON COLUMN account_nonce_records.created_at IS '创建时间';

COMMENT ON TABLE account_admin_tickets IS '管理端一次性票据';
COMMENT ON COLUMN account_admin_tickets.code IS 'ticket 编码';
COMMENT ON COLUMN account_admin_tickets.app_code IS '目标应用编码';
COMMENT ON COLUMN account_admin_tickets.user_id IS '关联用户 ID';
COMMENT ON COLUMN account_admin_tickets.purpose IS '用途：iframe-permission 等';
COMMENT ON COLUMN account_admin_tickets.expires_at IS '票据过期时间；有效期由服务端配置决定';
COMMENT ON COLUMN account_admin_tickets.used_at IS '使用时间（一次性，使用后标记）';
COMMENT ON COLUMN account_admin_tickets.created_at IS '创建时间';

COMMENT ON TABLE account_admin_roles IS '管理角色分配';
COMMENT ON COLUMN account_admin_roles.user_id IS '用户 ID';
COMMENT ON COLUMN account_admin_roles.role_code IS '角色编码：ACCOUNT_ADMIN / ACCOUNT_AUDITOR';
COMMENT ON COLUMN account_admin_roles.created_at IS '分配时间';

COMMENT ON TABLE account_idempotency_records IS '管理写请求幂等记录';
COMMENT ON COLUMN account_idempotency_records.id IS '记录唯一标识';
COMMENT ON COLUMN account_idempotency_records.idempotency_key IS '客户端幂等键；与操作者、HTTP 方法和路径共同限定唯一请求';
COMMENT ON COLUMN account_idempotency_records.operator_id IS '操作人用户 ID';
COMMENT ON COLUMN account_idempotency_records.request_method IS '请求 HTTP 方法';
COMMENT ON COLUMN account_idempotency_records.request_path IS '规范化资源路径';
COMMENT ON COLUMN account_idempotency_records.request_hash IS '请求内容摘要；拒绝幂等键相同而内容不同的请求';
COMMENT ON COLUMN account_idempotency_records.status IS '幂等状态：processing 处理中、completed 已完成';
COMMENT ON COLUMN account_idempotency_records.response_status IS '完成后的 HTTP 状态码；处理中为空';
COMMENT ON COLUMN account_idempotency_records.response_body IS '可重放响应 JSON；敏感结果不持久化，保存空 JSON 对象';
COMMENT ON COLUMN account_idempotency_records.created_at IS '创建时间';
COMMENT ON COLUMN account_idempotency_records.expires_at IS '过期时间；保留期限由服务端定义';

COMMENT ON TABLE account_sync_commands IS '应用准入同步命令';
COMMENT ON COLUMN account_sync_commands.id IS '记录唯一标识';
COMMENT ON COLUMN account_sync_commands.idempotency_key IS '命令级幂等键，全局唯一';
COMMENT ON COLUMN account_sync_commands.user_id IS '目标用户 ID，关联 account_users.id';
COMMENT ON COLUMN account_sync_commands.app_code IS '目标应用编码，关联 account_applications.app_code';
COMMENT ON COLUMN account_sync_commands.desired_status IS 'Account 期望准入状态：enabled / disabled；不代表业务应用已同步';
COMMENT ON COLUMN account_sync_commands.sync_version IS '命令对应的准入版本，用于幂等和乱序保护';
COMMENT ON COLUMN account_sync_commands.status IS '命令状态；当前为 pending_application_adaptation，尚未实际投递';
COMMENT ON COLUMN account_sync_commands.attempts IS '实际投递尝试次数，初始为 0';
COMMENT ON COLUMN account_sync_commands.next_retry_at IS '下次重试时间；尚未安排时为空';
COMMENT ON COLUMN account_sync_commands.error_code IS '最近失败的稳定错误码；禁止保存敏感错误详情';
COMMENT ON COLUMN account_sync_commands.trace_id IS '服务端关联 ID；用于请求或命令追踪';
COMMENT ON COLUMN account_sync_commands.created_at IS '创建时间';
COMMENT ON COLUMN account_sync_commands.updated_at IS '最后修改时间';
