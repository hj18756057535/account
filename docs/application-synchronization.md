# 应用准入托管同步（T-008）

本功能同步“用户能否使用应用”，不配置应用菜单或数据范围；后者属于后续 T-009。
管理页默认展示期望状态、实际命令状态及应用确认状态。未适配或未开启投递时显示“待应用适配”，不会伪报成功。

## 启用前提

默认 `account.application-sync.enabled=true`，页面与可靠投递链路开箱启用。真实目标仍只能由运维服务端明确配置，不能由管理 API 请求传入；未配置目标的应用不会发起网络请求，只显示不可投递状态。紧急停投时可显式设置为 `false`，但已纳入托管的应用不会因此绕过确认状态检查。
本次仅验证合成 Provider，没有启用或调用任何真实应用。

提供方完成下述持久化适配、开发者确认目标后，才配置：

```yaml
account:
  application-sync:
    enabled: true
    targets:
      your-app: https://your-application.example.invalid
```

示例地址不可用，请替换为开发者确认的真实地址。目标需与应用登记的 `notifyBaseUrl` 完全一致（忽略末尾一个 `/`），且应用启用、Secret 有效、协议包含 `user_sync`。必须 HTTPS；只有 `test` Profile 可使用字面回环 `127.0.0.1`/`[::1]` 的 HTTP。禁止 userinfo、query、fragment、路径归一化变化和重定向。

若使用上下文路径，`notifyBaseUrl` 应包含该 servlet context；发送地址追加 `/account-integration/users/{globalUserId}`，签名 path 不含 servlet context，与 Starter 过滤器规则一致。反向代理不得改变签名路径。

旧 `account.sync.type=http` 客户端使用时钟版本，不能直接接管此状态表。迁移同一应用前先停旧同步入口、等待在途结束，并由提供方显式处理旧版本/身份绑定后再开启托管目标；不可同时向同一应用使用两套版本序列。首次开启后不会批量投递历史命令，管理员需在页面重新同步或提交新的准入变更。

## 提供方契约

接口、字段和 HMAC 头见 `openapi/account-application-api.yaml`。签名每次刷新 timestamp/nonce，命令的 `Idempotency-Key`、syncVersion、occurredAt 和请求正文跨重试不变。

提供方必须：

1. 使用事务持久化 `(appCode, globalUserId) -> localUserId`、已应用版本、命令键、请求摘要和结果；多实例/重启后仍成立。
2. 同键同正文重放结果；同键不同正文、同版本不同请求和过时版本返回 409。不要将更高版本的状态作为旧命令成功结果。
3. 身份未知时明确创建，不依据 account/email 猜测或合并；禁用先于允许到达时也要保存禁用的身份/版本，不得激活用户。
4. 只有事务提交后返回 HTTP 200，应用、全局用户、状态、版本均与请求完全一致；本地 ID 非空且最多 255 字符；`resultCode` 为 `APPLIED` 或 `ALREADY_APPLIED`。
5. 自行保证当前业务会话受禁用控制。Account 拦截新入口，不承诺立即注销应用内既有会话。

Starter `AccountUserSyncGuard` 仅作单进程基础保护，不可作为生产持久化幂等。真实 Handler 应覆盖新增的 `apply(String idempotencyKey, AccountUserDesiredState desiredState)`，在业务事务中保存键、请求摘要、版本及结果；默认实现委托旧单参数入口，仅保持兼容。本次不修改任何真实业务应用。

## 状态、重试与数据保留

- 新准入写入与命令创建同事务；网络投递在事务外，连接 3 秒/请求 5 秒，响应上限 64 KiB，请求上限 16 KiB。
- 任务按批最多 20 个，30 秒租约，按应用→准入→命令顺序加锁，回执必须仍持有租约且版本匹配。同步内部事务使用 READ COMMITTED，避免 MySQL RR 的锁前快照读到旧映射/配置。
- 网络异常、429、5xx 最多尝试 5 次，等待 5/30/120/300 秒；业务冲突、非法回执等直接失败。管理员仅可重试当前失败或待适配命令，不改变准入版本。
- `POST /api/users/{userId}/applications/{appCode}/synchronizations`：管理员会话 + CSRF + Idempotency-Key，正文 `{expectedVersion}`，202 返回完整准入状态；重放同 key 不重复调度。
- 应用配置版本变化暂停原投递，手动重试表示重新确认当前目标；仍使用原请求正文。变更租户等业务正文应创建新准入命令，而不是重放旧命令。
- 成功/被新版本替代立即清除含个人信息的请求正文，失败最多保留 7 天，到期不可原键重建正文。数据库及备份应按部署要求限制读取权限和保存期限。
- `managed_sync` 在第一次实际排队后持久化。此后即使关闭投递，登录/票据入口也要求准入启用且当前版本已有应用确认；关闭开关不会回退为旧的直接放行。
- 禁用立即拦截 Account 新入口；重新启用已映射用户需明确确认可能复用旧菜单/数据权限。

## 数据库升级与验收

从本仓库已整理的完整 V1 自动升级 V2，新增映射表及投递字段，保留已有数据；MySQL COMMENT 内联、PostgreSQL COMMENT ON 均在各自 V2 中完整提供。不要修改已运行 V1 的校验和，也不要在此升级时重置数据库。旧历史 V1～V8 测试库仍需先按前次基线整理说明由开发者处理。

已用 H2 的 PostgreSQL/MySQL 模式验证 V1→V2 数据保留和 11 表/116 字段注释；实际 PostgreSQL/MySQL 锁语义仍需部署验收。
人工检查：中英切换、启用后待同步→确认、失败重试、禁用后不再放行、重新启用确认。浏览器测试由开发者执行。
回退只关闭新投递、等待在途结束，保留映射/命令表；不删应用用户、不回滚实际权限、不删除 `managed_sync` 标记。
