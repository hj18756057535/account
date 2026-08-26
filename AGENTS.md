# AGENTS.md

本文件是本仓库的 AI 协作入口，面向 Codex、Claude Code 等可读取项目规则的编码助手。目标是让 AI 在动手前快速判断：代码放哪里、边界在哪里、必须验证什么。

## 先读规则

1. 先基于仓库事实判断包归属、服务边界、表归属、配置入口和既有实现模式。
2. 不确定时先搜索现有 Controller、Service、Store、Mapper、启动类和配置文件，不只凭类名猜测。
3. 修改必须紧贴当前任务。不要顺手做无关重构、全仓库格式化、批量替换历史风格或移动模块目录。
4. 不要回滚用户已有修改。遇到无关未跟踪或已修改文件，保持原样。
5. 代码改动必须做受影响模块构建或说明无法构建的原因；文档改动做结构和空白检查。

## 项目概览

Account Center 是内部账号中心服务，提供用户管理、应用（子系统）注册、用户-应用授权和 SSO 单点登录能力。

- **运行时**: Java 21 LTS
- **框架**: Spring Boot 3.5.16 + MyBatis Spring Boot Starter 3.0.5 + Flyway
- **数据库**: PostgreSQL（主）、MySQL（备选）、H2（测试）
- **模板**: Thymeleaf（管理后台 UI）
- **端口**: 8088
- **构建**: `.\mvnw.cmd clean package -DskipTests`

## 包归属

根包 `com.hypers.account`，根 POM 聚合 `account-center-contract`、`account-center-server` 与 `account-center-spring-boot-starter`。Server 的唯一有效源码、资源和测试目录是 `account-center-server/src/**`；框架升级期间遗留且不参与构建的根 `src/**` 已于 2026-08-25 按开发者确认删除，不得重新建立根源码目录。

- `account-center-contract`：只保存跨进程请求/响应类型和协议常量，不依赖 Spring 或数据库。
- `account-center-spring-boot-starter`：业务系统引入的双向集成制品，内部按 `client`、`spi`、`autoconfigure`、`web`、`security`、`properties` 分包。
- `account-center-server`：服务端领域和持久化实现，只依赖 Contract，不依赖 Starter。

| 包 | 职责 |
| --- | --- |
| `app` | 领域模型和业务逻辑。包含实体（AccountUser、AccountApplication）、命令对象（SaveUserCommand、RegisterApplicationCommand）、Store 接口及实现、AccountDirectoryService。 |
| `mapper` | MyBatis Mapper 接口。只定义数据访问方法，不包含业务逻辑。 |
| `web` | Controller 层。包括 REST API（AccountDirectoryController、SsoTicketController）、管理后台页面（HomeController）、全局异常处理（AccountExceptionHandler）。 |
| `sso` | SSO 单点登录能力。SsoTicketService 负责签发和兑换 ticket，AccountUserSnapshot 和 SsoUserPayload 定义 SSO 数据传输结构。 |
| `security` | 安全能力。HmacSignatureService 负责 HMAC 签名验证。 |
| `config` | Spring 配置。AccountCenterConfiguration 管理 Bean 组装、Store 实现切换和 SSO 参数。 |

## 业务边界

- **用户管理**: `account_users` 表，CRUD、启用/禁用、按关键字搜索。账号（account）全局唯一。
- **应用管理**: `account_applications` 表，注册/更新子系统、密钥轮换、启用/禁用。appCode 全局唯一。
- **授权管理**: `account_user_applications` 关联表，用户与应用的授权/取消授权关系。授权时同步用户到应用，取消授权时通知应用禁用。
- **SSO 单点登录**: SsoTicketController 提供 ticket 签发和兑换接口，SsoTicketService 基于内存存储管理 ticket 生命周期（一次性、有 TTL）。
- **HMAC 签名**: HmacSignatureService 提供请求签名校验能力，用于应用间安全通信。
- **操作日志**: `account_operation_logs` 表记录关键操作审计。

## 分层架构

本项目采用 Store 模式而非传统 Service + Repository 分层：

```
Controller -> AccountDirectoryService -> AccountStore (接口)
                                          |-- MyBatisAccountStore (生产)
                                          |-- InMemoryAccountStore (测试/演示)
```

- **Store 接口** (`AccountStore`): 定义数据访问契约，封装所有持久化操作。
- **Store 实现**: `MyBatisAccountStore` 通过 Mapper 操作数据库，`InMemoryAccountStore` 用于测试和演示。通过 `account.store.type` 配置切换。
- **AccountDirectoryService**: 编排业务逻辑，调用 Store 和 ApplicationUserSyncClient。
- **Mapper**: 薄层 MyBatis 接口，XML 定义 SQL。Mapper 只被 Store 实现调用，Controller 和 Service 不直接调用 Mapper。

## Java 代码硬规则

生成或修改 Controller、Service、Store、Mapper、Entity、DTO、Command 等 Java 代码时，必须参考 `.agents/skills/account-code-rules/SKILL.md`。

高频底线：
- 依赖注入使用构造器注入（项目统一风格），不使用 `@Autowired` 或 `@Resource` 注解。
- Java 新增/修改代码使用 Lombok 消除 DTO、Command、值对象和构造器样板；敏感对象不得无差别使用 `@Data` 或生成包含密码、Secret、Token、Session 的 `toString`。
- API 默认通过 Git、制品和发布版本迭代；普通 URL、OpenAPI 文件名、配置键、Java 包、业务类、DTO、异常和测试类名不带 `v1` / `V1`。确需并行兼容时必须单独设计迁移窗口。
- Controller 不直接调用 Mapper；业务逻辑写在 Service/Store 层。
- 返回 `List` 时使用空集合，不返回 `null`。
- 领域实体（AccountUser、AccountApplication）是纯 POJO，不使用 ORM 注解。
- Mapper 接口使用 `@Mapper` 注解，SQL 定义在对应 XML 文件中。
- MyBatis XML 中的 `resultMap` 必须完整映射所有查询字段到 Java 属性。
- 模糊查询在 XML 中使用 `concat('%', #{keyword}, '%')`，不在 Java 代码中拼接。
- 命令对象（Command）负责入参封装和传递，Controller 内部类负责入参校验。
- 简单代码不写废话注释；复杂业务规则、跨表组装需要简短功能注释。

## 本地 Skill 触发

| Skill | 必须使用场景 |
| --- | --- |
| `.agents/skills/account-code-rules` | 生成或修改 Java 代码，包括 Entity、Command、DTO、Service、Store、Controller、Mapper。 |
| `.agents/skills/account-database-rules` | 新增或修改表、字段、索引、Mapper XML SQL、Flyway 迁移脚本。 |
| `.agents/skills/account-query-performance` | 优化慢查询、N+1、循环查库、批量查询或集合映射。 |

`.agents/skills` 是本仓库 Account 专属 Skill 的唯一规范源；`.claude/skills` 只允许使用本地 Junction 适配，不维护正文副本。如果本地 Skill 与仓库文档重叠，以更具体、更贴近当前任务的规则为准；如果冲突，先说明冲突并选择风险更低的做法。

## 数据库与部署

- 数据库迁移使用 Flyway，脚本位于 `account-center-server/src/main/resources/db/migration/`。
- 新增表、字段变更只新增对应版本的增量迁移脚本（`V{N}__{description}.sql`）。
- Flyway 脚本命名格式: `V{版本号}__{描述}.sql`，版本号递增，描述用下划线分隔。
- 数据库表名统一使用 `account_` 前缀。
- Mapper XML 位于 `account-center-server/src/main/resources/mapper/`，不要随意迁移目录。
- 主键策略: 用户表使用 UUID 字符串（`varchar(64)`），应用表使用业务编码 `app_code`。
- 所有表包含审计字段: `created_by`、`updated_by`、`created_at`、`updated_at`。
- 环境变量: `ACCOUNT_DB_URL`、`ACCOUNT_DB_USERNAME`、`ACCOUNT_DB_PASSWORD`，开发环境有默认值。

## 安全与隐私

- 不要把密钥、token、数据库密码、客户资料写入代码、示例文档或提交内容。
- 文档中使用占位符，真实值走环境变量或配置中心。
- `account_applications.secret` 是敏感字段，日志和响应中不得暴露。
- SSO ticket 是一次性凭证，兑换后立即失效。

## 开发环境与命令

- 团队本地以 Windows 为主。命令、脚本和排查步骤优先提供 PowerShell / Windows 可执行方式。
- 仓库已提供 Maven Wrapper；不要默认存在 GNU 工具链、`bash`、`sed -i`、`awk`、`kubectl`、`docker` 等命令。
- 中文 Markdown、配置示例、SQL 和 Java 注释保持 UTF-8。
- 新增团队本地脚本优先提供 `.bat` 或 PowerShell 版本；如果只提供 `.sh`，必须说明运行环境。

## 构建与验证

- 仓库使用 Maven Wrapper 3.3.4，固定 Maven 3.9.14。
- 构建: `.\mvnw.cmd clean package -DskipTests`
- 测试: `.\mvnw.cmd test`（测试使用 H2 内存数据库，无需外部依赖）
- 本地构建和运行只支持 JDK 21；执行前确认 `JAVA_HOME` 与 PATH 指向 JDK 21。
- 为提升协作效率，不要在每次小改后都执行 Maven 构建；优先做 `git diff --check`、定向静态检查和代码自检，只有在关键节点、完成阶段或用户明确要求时再执行 Maven 构建。
- 仅修改注解、VO/DTO 字段说明、国际化文案、Markdown 文档等低风险内容时，默认不跑完整 Maven 构建；最终回复必须明确说明做了哪些轻量验证，以及未跑 Maven 构建。
- 本地/开发启动需要 PostgreSQL；测试使用 H2 内存数据库，无外部依赖。

## 最终回复要求

最终回复必须说明：
- 改了哪些文件。
- 做了哪些验证。
- 哪些文件或风险点没有处理。
- 如果无法验证，说明原因，不要声称未验证内容已经通过。
