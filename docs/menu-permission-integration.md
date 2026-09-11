# 菜单权限 Provider 联调（T-009）

`menu_permission_v1` 只管理已准入用户在业务应用内的菜单/操作权限。Account 保存应用与准入状态，不保存应用菜单明细；查询和整集替换均由 Account Server 调用应用 Provider，浏览器不直连业务应用。

## 启用条件

1. Account 中应用必须启用、Secret 有效，`notifyBaseUrl` 与服务端 `account.application-sync.targets.<appCode>` 完全一致，并声明 `user_sync` 与 `menu_permission_v1`。
2. 用户准入必须为 enabled，且 T-008 用户同步已成功确认当前版本并返回非空 `localUserId`。
3. 业务应用 Provider 使用同一 appCode/Secret，两个固定路径能够经网关转发且早于应用 JWT 过滤器执行 HMAC 校验。

Account 目标配置沿用 T-008，不增加由管理请求控制的任意地址：

```yaml
account:
  application-sync:
    enabled: true
    targets:
      cms-ai: https://your-cms-gateway.example.invalid/api/main
```

真实地址必须与应用登记的 `notifyBaseUrl` 一致。生产环境只允许 HTTPS；禁止重定向、userinfo、query、fragment 和非规范化路径。

`cms-ai` 从环境变量或 Nacos 注入配置，仓库不保存 Secret：

```text
ACCOUNT_MENU_PERMISSION_ENABLED=true
ACCOUNT_MENU_PERMISSION_APP_CODE=cms-ai
ACCOUNT_MENU_PERMISSION_SECRET=<与 Account 应用 Secret 相同>
```

未提供 Secret 时 Provider 不注册；显式设置 `ACCOUNT_MENU_PERMISSION_ENABLED=false` 可紧急关闭。网关转发 `/api/main/account-integration/**` 后，应用内实际固定路径仍为 `/account-integration/menu-permissions/query` 和 `/account-integration/menu-permissions`，签名使用固定路径，不包含网关前缀。

## 人工联调顺序

1. 启动 Redis、`cms-ai`、网关和 Account，确认两端 appCode、Secret、时钟和目标地址一致。
2. 在 Account 应用登记中勾选 `menu_permission_v1`，先完成用户准入及 T-008 同步，确认页面显示同步成功且当前版本一致。
3. 打开用户的菜单权限，检查中文名称及英文回退、原生角色继承项只读、托管项可以整集保存。
4. 保存后让目标用户重新登录 `cms-ai`，核对菜单和按钮变化；原生角色权限仍保留，超级管理员应被拒绝管理。
5. 使用相同幂等键重试相同请求应复用结果；修改 revision 或同键换正文应返回冲突。检查日志和审计中没有 Secret 或完整菜单选择。

## 回退

先从应用能力移除 `menu_permission_v1` 或关闭 Provider，再停止相关请求。回退不会自动删除 `cms-ai` 中 `account_mp_` 前缀的托管角色及最后一次授权，避免破坏性撤权；如需恢复用户权限，由 `cms-ai` 管理员在确认影响后显式处理。Account 无菜单权限表或数据迁移需要回滚。
