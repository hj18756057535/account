# Account Center

内部统一账户中心，提供用户管理、应用准入授权和 SSO 单点登录能力。

## 当前需求与数据库结构

### Excel 用户导入

默认启用，无需增加启动参数；升级重启后重新登录/刷新会话，管理员用户列表显示“导入用户”。
可通过 `--account.user-import.enabled=false` 显式关闭。功能仅支持 MyBatis
数据库 Store；内存演示模式不提供无法原子回滚的导入入口。关闭开关后全部导入端点拒绝，
普通用户 CRUD 保持不变。不需要配置客户接口或应用白名单。

- 下载 XLSX 模板，按 account/email/name/phone 四列填写文本；手机号保留前导零。
- 最大 5 MiB、1000 条；仅单工作表，无公式、合并单元格、外部链接、宏或图片/嵌入附件。
- 上传后预览 15 分钟有效，先修正所有错误再确认；已有账号不覆盖，提交全成或全败。
- 重试同批次不会重复创建，成功结果保留 24 小时；提交立即清除暂存个人资料，
  过期记录每分钟最多清理 100 批。暂存 JSON 另限 4 MiB，不保存原始文件。
- 导入只创建用户档案，不设置密码、管理员角色、应用准入或应用内权限。
- 导入表已包含在完整 V1 初始化基线中；MySQL 的 JSON 文本列直接使用 LONGTEXT，
  PostgreSQL/H2 使用 TEXT，所有字段在初始化时即包含数据库注释。
- 回退先关闭入口并等待在途事务完成，保留新表和已导入用户，不删除业务数据。

客户用户接口本期仅保留适配设计，未实际接入客户环境；Excel 不依赖客户接口。

- 后端中英支持覆盖管理 API、旧票据/签名错误、框架兜底及登录/SSO 模板；Starter 集成错误也支持 Accept-Language。只翻译展示消息，业务编码、数据和签名保持不变；未知内部错误不直接返回给调用方。SSR 页面使用浏览器请求语言，不读取 Vue 的 localStorage。

- 需求正文统一保存在工作区 `requirements/features/ACCOUNT-001/requirement.md`；执行范围以 `.agent-work/ACCOUNT-001/` 中已批准的规格、设计和计划为准。旧后端需求/SSO 计划已移除，保留 [架构说明](docs/architecture.md)、[接入指南](docs/integration-guide.md) 与 [菜单权限联调说明](docs/menu-permission-integration.md)。
- 数据库现采用完整初始化基线：[PostgreSQL SQL](account-center-server/src/main/resources/db/vendor/postgresql/V1__init_account_center.sql)、[MySQL SQL](account-center-server/src/main/resources/db/vendor/mysql/V1__init_account_center.sql)。每份包含 10 张表、99 个字段、约束、索引及全部数据库注释，不再保留旧 V1～V8 拆分脚本或重复 schema.sql。
- **仅用于空库**：开发者已确认当前为可重建测试库。先停止旧应用并自行备份/处理数据，提供空数据库或空 schema（旧业务表与旧 flyway_schema_history 均不能残留）；清理旧构建产物后启动，新代码按数据库类型只执行对应 V1，并由 Flyway 自动记录历史。不要同时手工执行 SQL 和应用自动初始化；SQL 文件可直接审阅，默认交给 Flyway 执行。
- 代码强制关闭自动 baseline、禁止 Flyway clean，不自动清库，不兼容旧迁移历史；发现旧库时停止并检查，不使用 repair 忽略差异。若选择手工执行 SQL，需另行正确建立版本 1 的 Flyway baseline，不要伪造历史记录。临时 sql/init.sql 不作为入口，也不参与构建。
- PostgreSQL/MySQL 实库仍需开发者验证。新基线无旧数据回填或降级 SQL；恢复旧应用须同时恢复对应的数据库备份与旧迁移历史。后续正式使用后保持迁移不可变，只新增版本。

## 功能特性

- **用户管理** — 用户 CRUD、启用/禁用、按关键字搜索
- **应用管理** — 应用注册、配置更新、密钥轮换、启用/禁用
- **授权管理** — 用户-应用授权/取消授权，授权时自动同步用户到应用
- **SSO 单点登录** — 标准浏览器授权码流程，应用跳转 → 登录 → 回调
- **应用同步** — 通过 HMAC 签名的 HTTP POST 通知应用用户变更
- **管理 ticket** — iframe 授权页面的短期凭证（60 秒，一次性）
- **审计日志** — 记录所有关键管理操作
- **管理后台** — Thymeleaf 页面，支持用户/应用/授权/日志管理

## 快速开始

### 环境要求

- JDK 21
- Maven Wrapper（固定 Maven 3.9.14）
- PostgreSQL 12+（或 MySQL 8.0+）

### 构建

```powershell
.\mvnw.cmd clean package -DskipTests
```

### 启动（PostgreSQL）

```powershell
$env:ACCOUNT_DB_URL='jdbc:postgresql://localhost:5432/account_center'
$env:ACCOUNT_DB_USERNAME='account'
$env:ACCOUNT_DB_PASSWORD='<local-password>'

java -jar account-center-server/target/account-center-server-0.1.0-SNAPSHOT.jar
```

### 启动（MySQL）

```powershell
$env:ACCOUNT_DB_URL='jdbc:mysql://localhost:3306/account_center?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:ACCOUNT_DB_USERNAME='account'
$env:ACCOUNT_DB_PASSWORD='<local-password>'

java -jar account-center-server/target/account-center-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```

### 运行测试

```powershell
.\mvnw.cmd test
```

测试使用 H2 内存数据库，无需外部依赖。

## 项目结构

```
account-center/
├── pom.xml                              # 父 POM
├── account-center-server/               # 服务端模块；Java、资源、测试唯一事实源
│   └── src/
│       ├── main/java/com/hypers/account/
│       │   ├── app/                     # 领域模型、Store、Service
│       │   ├── auth/                    # 登录认证
│       │   ├── admin/                   # 管理 ticket
│       │   ├── audit/                   # 审计日志
│       │   ├── mapper/                  # MyBatis Mapper 接口
│       │   ├── security/                # HMAC 签名
│       │   ├── sso/                     # SSO ticket
│       │   ├── web/                     # Controller
│       │   └── config/                  # Bean 配置
│       ├── main/resources/              # 配置、Flyway、Mapper XML、模板
│       └── test/                        # 测试
├── account-center-contract/             # 双向远程契约与协议常量
├── account-center-spring-boot-starter/  # 业务应用引入的 Client + Provider SPI/自动装配
└── docs/
    ├── architecture.md                  # 架构设计文档
    └── integration-guide.md             # 应用接入指南
```

## 分层架构

```
Controller → Service → Store(接口) → MyBatisAccountStore → Mapper → 数据库
                                    → InMemoryAccountStore (测试)
```

- **Controller** — 参数校验、请求路由
- **Service** — 业务编排
- **Store** — 数据访问抽象
- **Mapper** — MyBatis SQL 映射

## API 概览

| 分类 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 用户 | POST | `/api/users` | 新增用户 |
| 用户 | GET | `/api/users?keyword=&status=` | 搜索用户 |
| 用户 | GET/PUT | `/api/users/{id}` | 查看/编辑 |
| 用户 | POST | `/api/users/{id}/enable` | 启用 |
| 用户 | POST | `/api/users/{id}/disable` | 禁用（同步通知应用） |
| 应用 | POST | `/api/applications` | 注册应用 |
| 应用 | GET | `/api/applications?query=&status=` | 搜索应用（脱敏） |
| 应用 | GET/PUT | `/api/applications/{code}` | 查看/编辑 |
| 应用 | PUT | `/api/applications/{code}/status` | 启用/禁用 |
| 应用 | POST | `/api/applications/{code}/secret/rotate` | 密钥轮换 |
| 应用 | POST | `/api/applications/{code}/secret/revoke` | 撤销密钥 |
| 准入 | GET | `/api/users/{id}/application-access` | 查询准入期望状态 |
| 准入 | PUT | `/api/users/{id}/application-access/{code}` | 保存期望状态，202/待应用适配，不执行远程同步 |
| SSO | GET | `/sso/authorize` | SSO 浏览器流程 |
| SSO | POST | `/openapi/sso/tickets/exchange` | 兑换 code |
| Ticket | POST | `/openapi/admin-tickets/verify` | 校验管理 ticket |
| 审计 | GET | `/api/audit-logs` | 查询审计日志 |

管理端契约以 [openapi/account-api.yaml](openapi/account-api.yaml) 为准。应用/准入写操作要求管理员 Session、CSRF、`Idempotency-Key`，更新携带资源 `version`。Secret 仅创建/轮换成功时展示一次，同键重试不重放明文；普通查询不返回 Secret。架构说明见 [docs/architecture.md](docs/architecture.md)。

## 应用接入

Spring Boot 业务应用引入 `account-center-spring-boot-starter` 即可同时调用 Account，并向 Account 暴露标准用户同步端点：

```xml
<dependency>
    <groupId>com.hypers</groupId>
    <artifactId>account-center-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

详细对接步骤见 [docs/integration-guide.md](docs/integration-guide.md)。

## 管理后台

访问 `http://localhost:8088` 进入管理后台：

- `/` — 首页导航
- `/users` — 用户管理与授权
- `/applications` — 跳转 `/console/applications`（由独立 account-web 提供）
- `/audit-logs` — 审计日志
- `/login` — 登录

## 技术决策

| 决策 | 说明 |
|------|------|
| 不引入 Spring Security | 仅使用 `spring-security-crypto` 做 BCrypt，避免自动配置干扰 |
| MyBatis 而非 JPA | SQL 与 Java 分离，方便 DBA 审核和调优 |
| Flyway 迁移 | 版本化管理数据库变更，支持多环境自动迁移 |
| Store 模式 | 数据访问抽象层，测试时可切换为 InMemoryAccountStore |
| 三模块边界 | Server 与 Starter 分别只依赖 Contract；Server 不依赖 Starter |
| 双向 Starter | 自动装配 Account Client；业务应用实现 `AccountUserSyncHandler` 后暴露受 HMAC 保护的同步端点 |
| 同步使用 HTTP | 当前建立同步契约骨架；持久化 Outbox 在后续业务系统接入切片实现 |
