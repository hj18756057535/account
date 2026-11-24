# 应用准入托管同步（T-008）

本功能同步“用户能否使用应用”；菜单权限使用独立的标准菜单接口和页面。
管理页默认展示期望状态、实际命令状态及应用确认状态。未适配或未开启投递时显示“待应用适配”，不会伪报成功。

## 启用前提

`notifyBaseUrl` 和当前 Secret 由应用管理页面登记，无需逐应用目标映射或协议能力勾选。填写后端地址即使用标准 Provider；未填写时不创建同步命令、不发起网络请求。默认 `account.application-sync.enabled=true`，这是全局运维停投开关，停投不免除已配置 Provider 的当前版本确认条件。
本次仅验证合成 Provider，没有启用或调用任何真实应用。

提供方完成下述持久化适配后，在管理页面登记服务基址；全局开关仅在需要停投时修改：

```yaml
account:
  application-sync:
    enabled: true
```

应用需启用、Secret 有效并填写合法的后端服务地址。目标直接读取页面 `notifyBaseUrl`，默认 HTTPS；本机 `localhost`、`127.0.0.1`、`[::1]` 允许 HTTP，不依赖 test Profile。禁止 userinfo、query、fragment、路径归一化变化和重定向。服务请求添加 `X-Account-Protocol-Version: account_provider`。

若使用上下文路径，`notifyBaseUrl` 应包含该 servlet context；发送地址追加 `/account-integration/users/{globalUserId}`，签名 path 不含 servlet context，与 Starter 过滤器规则一致。反向代理不得改变签名路径。

当前只保留版本化可靠投递链路，已移除立即同步客户端及其配置绑定。管理员授权后才同步，授权和撤权不要求理由，审计自动记录。先授权、后填写后端地址的应用可在页面明确点击重新同步，创建当前版本命令；不会自动批量投递。

## 提供方契约

接口、字段和 HMAC 头见 `openapi/account-application-api.yaml`。签名每次刷新 timestamp/nonce，命令的 `Idempotency-Key`、syncVersion、occurredAt 和请求正文跨重试不变。

提供方必须：

1. 使用事务持久化 `(appCode, globalUserId) -> localUserId`、已应用状态、版本和请求摘要；可以合并到应用已有身份关联表，多实例/重启后仍成立。
2. 当前版本相同内容返回 `ALREADY_APPLIED`，不重复更新资料或授权；同版本不同内容及旧版本返回 409，不承诺重放历史响应。`Idempotency-Key` 用于跟踪和重试，不要求独立保存逐请求回执。不要将更高版本状态作为旧命令成功结果。
3. 身份未知时遵循应用已确认的关联规则，不依据 email 猜测或转移身份。CMS 按用户明确要求的唯一账户名首次关联，随后使用稳定 ID；见 [当前设计](../../docs/application-integration/CMS页面接入与目录同步-设计.md)。禁用先于允许到达时也要保存禁用的身份/版本，不得激活用户。
4. 只有事务提交后返回 HTTP 200，应用、全局用户、状态、版本均与请求完全一致；本地 ID 非空且最多 255 字符；`resultCode` 为 `APPLIED` 或 `ALREADY_APPLIED`。
5. 自行保证当前业务会话受禁用控制。Account 拦截新入口，不承诺立即注销应用内既有会话。

CMS 使用 Boot 2 Starter 的 `apply(String idempotencyKey, AccountUserDesiredState command)`，按 [单表精简方案](../../docs/application-integration/CMS同步表精简-设计.md) 在业务事务中维护当前版本及摘要，不保存逐请求历史结果。进程内 Guard 不可替代持久化幂等。

## 状态、重试与数据保留

- 已配置后端地址时，新准入写入与命令创建同事务；未配置时只保存准入。网络投递在事务外，连接 3 秒/请求 5 秒，响应上限 64 KiB，请求上限 16 KiB。
- 任务按批最多 20 个，30 秒租约，按应用→准入→命令顺序加锁，回执必须仍持有租约且版本匹配。同步内部事务使用 READ COMMITTED，避免 MySQL RR 的锁前快照读到旧映射/配置。
- 网络异常、429、5xx 最多尝试 5 次，等待 5/30/120/300 秒；业务冲突、非法回执等直接失败。管理员可重试当前失败或待适配命令，不改变该命令的版本；补齐配置后首次同步已有准入时创建新版本命令。
- `POST /api/users/{userId}/applications/{appCode}/synchronizations`：管理员会话 + CSRF + Idempotency-Key，正文 `{expectedVersion}`，202 返回完整准入状态；重放同 key 不重复调度。
- 应用配置版本变化暂停原投递，手动重试表示重新确认当前目标；仍使用原请求正文。变更租户等业务正文应创建新准入命令，而不是重放旧命令。
- 成功/被新版本替代立即清除含个人信息的请求正文，失败最多保留 7 天，到期不可原键重建正文。数据库及备份应按部署要求限制读取权限和保存期限。
- 登录、票据和菜单入口读取当前地址配置；存在后端地址时要求准入启用且当前版本已有应用确认，全局停投不改变这个条件。没有独立的托管标记或协议能力持久化列。
- 禁用立即拦截 Account 新入口；重新启用已映射用户需明确确认可能复用旧菜单/数据权限。

## 数据库初始化与验收

系统尚未使用，表结构合并在双方言最新 V1 中，共 14 表、166 字段，包含同步、目录、OIDC 及四项审计字段。删除未发布的增量脚本和回填，使用空数据库或空 schema 初始化；已有本地开发数据由开发者自行处理，应用不自动清库、baseline 或 repair。

已用 H2 的 PostgreSQL/MySQL 模式验证最新 V1 初始化、全部字段中文注释和审计结构；实际 PostgreSQL/MySQL 锁语义仍需部署验收。
人工检查：四语切换、授权后待同步→确认、失败重试、撤权后不再放行、重新授权确认。浏览器测试由开发者执行。
停投使用全局运维开关，等待在途结束；阻止访问使用应用禁用。保留映射与命令，不删除应用用户或回滚实际权限。
