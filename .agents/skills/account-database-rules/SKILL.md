---
name: account-database-rules
description: 在 Account Center 仓库中新增或修改表、字段、索引、Mapper XML、Flyway 迁移或其他影响数据库兼容性的内容时使用。
---

# Account Center 数据库规则

本 Skill 用于数据库、SQL、Mapper XML 和 Flyway 迁移脚本相关改动。

## 工作流程

1. 确认表属于用户、应用、授权、操作日志、nonce、ticket 还是管理角色。
2. 搜索现有 Mapper/XML 和 Flyway 脚本，避免重复建表或误改历史表。
3. 同步修改 Mapper 接口、Mapper XML、Flyway 迁移脚本。
4. 检查索引、唯一键、字段长度、默认值和空值约束是否匹配业务。
5. 执行受影响模块编译；SQL/YAML 改动还要做结构和敏感信息检查。

## 表归属

所有表统一使用 `account_` 前缀：

| 表 | 用途 |
| --- | --- |
| `account_users` | 用户主表。id 为 UUID 字符串，account 全局唯一。 |
| `account_applications` | 应用（子系统）主表。主键为 app_code 业务编码。 |
| `account_user_applications` | 用户-应用授权关联表。复合主键 (user_id, app_code)，外键关联上述两表。 |
| `account_operation_logs` | 操作审计日志表。 |
| `account_nonce_records` | 一次性 nonce 记录，防重放。 |
| `account_admin_tickets` | 管理端 ticket 表。 |
| `account_admin_roles` | 管理员角色关联表。复合主键 (user_id, role_code)。 |

## 同步清单

数据库变更必须同步：

- **Mapper 接口**: 新增/修改方法签名，参数使用 `@Param` 注解。
- **Mapper XML**: SQL 语句、`resultMap`、字段映射。`resultMap` 必须覆盖所有查询字段。
- **Flyway 迁移脚本**: 新增版本化迁移脚本 `V{N}__{description}.sql`。
- **领域实体**: 如新增字段，同步 `AccountUser` / `AccountApplication` 等 POJO。
- **配置**: 涉及数据源、Flyway 配置时检查 `application.yml` 和多环境配置。

## 字段规范

- **主键**: 用户表 `id varchar(64)`（UUID），应用表 `app_code varchar(64)`（业务编码）。
- **审计字段**: 所有主表必须包含 `created_by`、`updated_by`、`created_at`、`updated_at`。
- **状态字段**: 使用 `varchar(32)`，值为 `enabled` / `disabled`，默认 `enabled`。
- **时间字段**: 使用 `timestamp` 类型，`created_at` 默认 `current_timestamp`。
- **精度数值**: 使用 `DECIMAL(p,s)` / `BigDecimal`。
- **文本**: 大段文本使用 `text` 类型。
- **字段注释**: 使用中文注释说明字段用途。

## Flyway 迁移脚本

命名格式: `V{N}__{description}.sql`

```
V1__init_account_center.sql     -- 初始化建表
V2__add_user_avatar.sql         -- 新增字段示例
V3__create_audit_index.sql      -- 新增索引示例
```

规则：
- 版本号 `N` 递增，不可重复。
- 描述用下划线分隔，简洁说明变更内容。
- 只写增量变更（`ALTER TABLE`、`CREATE INDEX` 等），不修改已执行的历史脚本。
- 脚本位于 `src/main/resources/db/migration/`。
- Flyway 配置: `spring.flyway.enabled=true`、`locations=classpath:db/migration`、`baseline-on-migrate=true`。

示例：

```sql
-- V2__add_user_avatar.sql
ALTER TABLE account_users ADD COLUMN avatar_url VARCHAR(512);
COMMENT ON COLUMN account_users.avatar_url IS '用户头像地址';
```

```sql
-- V3__create_operation_log_index.sql
CREATE INDEX idx_operation_logs_operator ON account_operation_logs (operator_id);
CREATE INDEX idx_operation_logs_created ON account_operation_logs (created_at);
```

## Mapper XML 规范

- 位于 `src/main/resources/mapper/`，namespace 对应 Mapper 接口全限定名。
- `<resultMap>` 完整映射所有字段，不遗漏。
- 使用 `<where>` + `<if>` 做动态条件拼接。
- 参数使用 `#{}` 占位符，禁止 `${}` 拼接。
- 查询字段显式列出，禁止 `SELECT *`。
- 模糊查询用 `concat('%', #{keyword}, '%')`。
- 联表查询使用显式 `JOIN ... ON`，不隐式关联。
- 新增 Mapper XML 后在 `AccountUserMapper` 等接口中新增对应方法签名。

## 禁止

- 只改 Java 不改 SQL，或只改 SQL 不改 Mapper 接口/实体。
- 随意修改已执行的 Flyway 迁移脚本。
- Mapper XML 使用 `SELECT *`。
- 参数使用 `${}` 拼接（SQL 注入风险）。
- 在 SQL、文档或示例中写真实密钥、token、数据库密码。
- `InMemoryAccountStore` 和 `MyBatisAccountStore` 行为不同步。

## 验证

完成前确认：

- [ ] 表名使用 `account_` 前缀。
- [ ] 主键策略正确（UUID 字符串 / 业务编码）。
- [ ] 主表包含审计字段 `created_by`、`updated_by`、`created_at`、`updated_at`。
- [ ] Mapper 接口、XML、领域实体、Flyway 脚本已同步。
- [ ] `resultMap` 完整映射所有查询字段。
- [ ] 索引和唯一键有业务依据。
- [ ] 无敏感信息。
- [ ] 受影响模块可编译，或已说明无法编译原因。

