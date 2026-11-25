-- 默认 CMS 应用数据；Account 完成 V1 建表后执行此一条语句。
-- Secret 使用 CMS 默认 bootstrap.yml 中 account.integration.secret 的当前值；不生成或轮换凭据。
-- 本文件只保存占位符；实际值不要提交。已有 cms-ai 时继续页面维护，不覆盖已有记录。
INSERT INTO account_applications (
    app_code, name, entry_url, sso_callback_url, permission_iframe_url, notify_base_url,
    secret, default_tenant_code, status, secret_version, secret_state, version,
    created_by, updated_by, created_at, updated_at
) VALUES (
    'cms-ai', 'CMS 碳管理系统',
    'http://localhost:8850/api-test/main/login/account',
    'http://localhost:8850/api-test/main/login/account/callback',
    '', 'http://localhost:9101',
    '8Hf9cFdoGJoGfOSadDawHhRrb9W1W5NyrkhpYhcY5yw', 'default', 'enabled', 1, 'active', 1,
    'system', 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);
