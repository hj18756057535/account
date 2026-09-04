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
5. SQL 编写完成后立即执行下方注释检查，核对本次新增/修改表和字段，不得只写“已检查”而无检查数量、遗漏项及命令结果；缺失先补齐，再复查。
6. 执行与风险相称的定向验证；仅源码注释/参考文档改动不跑全量 Maven。SQL/YAML 仍需结构和敏感信息检查。

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
- **表/字段注释**: 新表和每个新增/修改字段必须有中文业务说明；必要时说明枚举编码、单位、默认值、空值、关联和敏感数据约束。不能只写表级说明、Java 注解，或以“简单字段”为由省略。

## SQL 写完后的注释检查（必做）

- 本项目新 DDL 使用可核查布局：CREATE TABLE 表头独占一行，紧前一行写中文表说明；每个字段定义独占一行，末尾写 `-- 中文说明`；闭合 `);` 独占一行。ADD COLUMN 每个字段一条语句，末尾同样写说明。
- 在 Account 仓库根执行下列命令；将 `-Path` 换成本次实际编写的 SQL 路径，可传路径数组。脚本只读，不连接数据库，不格式化或改写文件。

```powershell
pwsh -NoProfile -File .agents/skills/account-database-rules/scripts/Test-SqlComments.ps1 -Path account-center-server/src/main/resources/db/schema.sql
```

- 自动检查覆盖上述 CREATE TABLE / ADD COLUMN 源码注释，输出表数、字段数及缺失行，非零退出码必须处理；未知布局/未识别字段不能算通过。MODIFY/ALTER COLUMN、方言特有语法需逐字段人工核对并记录覆盖，不把本脚本称为通用 SQL 解析器。
- 修改检查器后运行 `pwsh -NoProfile -File .agents/skills/account-database-rules/scripts/Test-SqlComments.Tests.ps1`，验证缺失注释、字符串伪注释、未知布局与正常字段的正反例。
- 自动检查只证明有中文源码说明；还要人工核对说明与字段实际语义一致。SQL `--` 与数据库 COMMENT 是不同交付：需要数据库工具显示说明时，按目标方言另交付并验证元数据，不能拿源码注释替代。
- 核对主键、外键、唯一约束、索引、类型/长度、默认值与空值，并同步当前建表参考；PostgreSQL/MySQL/H2 兼容性按实际变更验证。MySQL 为加 COMMENT 而 MODIFY COLUMN 时完整保留列属性；PostgreSQL 的 COMMENT ON 不直接混入 MySQL 共用脚本。
- 不为消除检查失败修改已执行 Flyway 历史文件。历史缺失单列为债务，补到当前参考或获准的新迁移；本次新脚本有遗漏必须修复。只涉及索引/数据的脚本记录“无新增字段”并人工核对范围，不虚报字段检查通过。

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
- 脚本位于 `account-center-server/src/main/resources/db/migration/`。
- Flyway 配置: `spring.flyway.enabled=true`、`locations=classpath:db/migration`、`baseline-on-migrate=true`。

示例：

```sql
-- V2__add_user_avatar.sql
ALTER TABLE account_users ADD COLUMN avatar_url VARCHAR(512); -- 用户头像地址；为空表示尚未设置
-- 下句仅为 PostgreSQL 元数据示例，不直接放入 MySQL 共用迁移。
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
- [ ] 本次新建表及新增/修改字段均有中文说明；SQL 写完后已执行注释检查，记录覆盖数、缺失清单和退出码，并人工核对语义。
- [ ] 已区分源码注释与数据库 COMMENT；未改已执行迁移，未把历史缺失隐瞒为通过。
- [ ] 无敏感信息。
- [ ] 受影响模块可编译，或已说明无法编译原因。
