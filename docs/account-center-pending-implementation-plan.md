# Account Center 待实施功能计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Account Center MVP 基础上，补齐生产可用的用户管理、应用接入、接口同步、SSO 浏览器流程、iframe 授权票据、Starter 包、审计和 `cms-ai` 接入能力。

**Architecture:** Account Center 继续保持前后端不分离，一个 Spring Boot jar 承载管理页面、内部 API 和 OpenAPI。Account 只做统一账户、应用准入和 SSO 票据，各业务应用继续维护自己的角色、菜单、按钮和数据权限，Account 通过 iframe 打开应用自己的授权页面。参考 account-portal 的分层设计：Controller -> Service -> Mapper(MyBatis)，实体使用领域模型继承体系。

**Tech Stack:** Spring Boot 2.7.x、Java 11、Thymeleaf、MyBatis、Flyway、PostgreSQL 默认存储、MySQL profile、JUnit 5、MockMvc。

---

## 0. 当前已完成基线

- 已完成 Spring Boot 单体项目骨架。
- 已完成 PostgreSQL 默认配置和 MySQL profile。
- 已完成 JDBC 存储接口与实现（需迁移至 MyBatis）：
  - `src/main/java/com/hypers/account/app/AccountStore.java`
  - `src/main/java/com/hypers/account/app/JdbcAccountStore.java`
  - `src/main/resources/db/schema.sql`
- 已完成基础表：
  - `account_users`
  - `account_applications`
  - `account_user_applications`
- 已完成基础页面拆分：
  - `/`：首页导航
  - `/users`：用户创建、应用准入授权、SSO Code、iframe 调试
  - `/applications`：应用管理
- 已完成基础接口：
  - `POST /api/applications`
  - `POST /api/users`
  - `POST /api/users/{userId}/applications/{appCode}/authorize`
  - `POST /api/users/{userId}/applications/{appCode}/deauthorize`
  - `POST /api/sso/tickets`
  - `POST /openapi/sso/tickets/exchange`
- 已完成基础测试：SSO code、HMAC、授权同步、JDBC 存储、页面路由、API 集成。

## 0.1 JDBC -> MyBatis 迁移说明

原有代码使用 `JdbcTemplate` 做数据访问，需要全部迁移为 MyBatis：

**迁移策略：**
- 去掉 `spring-boot-starter-jdbc`，引入 `mybatis-spring-boot-starter`
- 创建 MyBatis Mapper 接口（`AccountUserMapper`、`AccountApplicationMapper`、`AccountUserApplicationMapper`）
- 创建对应的 XML Mapper 文件（`src/main/resources/mapper/*.xml`）
- 将 `JdbcAccountStore` 重命名为 `MyBatisAccountStore`，内部调用 Mapper
- 保留 `AccountStore` 接口不变，`InMemoryAccountStore` 不变（测试用）
- 配置 `mybatis.mapper-locations` 和 `mybatis.configuration.map-underscore-to-camel-case`

**参考 account-portal 的模式：**
- account-portal 使用 JPA + 自定义 Repository 规范模式
- 新项目使用 MyBatis Mapper + XML SQL 映射，保持轻量
- 实体类保留不可变 POJO 风格（构造函数注入）

## 1. 待实施功能总览

| 阶段 | 功能 | 目标 | 优先级 |
| --- | --- | --- | --- |
| P1 | JDBC -> MyBatis 迁移 | 将 JdbcTemplate 全部替换为 MyBatis Mapper | 高 |
| P1 | 数据库迁移与生产表结构 | 从 `schema.sql` 升级到 Flyway 可演进迁移，补齐状态、审计字段和唯一约束 | 高 |
| P1 | 用户管理完善 | 支持查询、编辑、启用、禁用、查看授权应用 | 高 |
| P1 | 应用管理完善 | 支持查询、编辑、启用、禁用、密钥轮换、默认租户兜底 | 高 |
| P1 | 应用接口同步客户端 | Account 授权/取消/编辑用户时真实调用应用接口 | 高 |
| P1 | HMAC 签名与 nonce 防重放 | 所有 Account 到应用的内部调用具备签名和重放保护 | 高 |
| P2 | 标准 SSO 浏览器流程 | 支持应用跳转 Account 登录/授权，再回调应用 | 高 |
| P2 | iframe 管理票据 | Account 打开应用授权页前生成短期管理票据，应用可校验 | 高 |
| P2 | 管理端登录与权限 | Account 管理页面需要登录保护，区分管理员和普通用户 | 中 |
| P2 | Starter 包 | 给各业务应用提供 DTO、签名校验、code 兑换客户端和接口骨架 | 高 |
| P3 | 审计与操作日志 | 记录用户、应用、授权、同步、SSO、iframe 票据相关操作 | 中 |
| P3 | `cms-ai` 接入 | 双碳服务接入 Account SSO、用户同步和 iframe 授权 | 高 |
| P3 | 运维部署文档 | 给出 PG/MySQL 初始化、环境变量、启动命令和回滚方式 | 中 |

## 2. 数据库与迁移计划

### 2.1 目标

当前 `schema.sql` 适合 MVP 启动。生产阶段需要迁移工具，避免每次启动都直接执行建表脚本，也便于后续升级字段和索引。

### 2.2 待建/待改文件

- Modify: `pom.xml`
  - 去掉 `spring-boot-starter-jdbc`。
  - 增加 `mybatis-spring-boot-starter` 依赖。
  - 增加 Flyway 依赖。
- Create: `src/main/resources/db/migration/V1__init_account_center.sql`
  - 承接当前 `schema.sql` 的三张表。
- Create: `src/main/resources/db/migration/V2__add_status_and_audit_columns.sql`
  - 给用户、应用、授权关系补齐状态和审计字段。
- Modify: `src/main/resources/application.yml`
  - 使用 Flyway 管理迁移。
  - 关闭 `spring.sql.init` 的生产初始化。
  - 添加 MyBatis 配置。
- Modify: `src/test/resources/application.yml`
  - 测试环境继续自动迁移。

### 2.3 表结构目标

`account_users` 增加：

- `status varchar(32) not null default 'enabled'`
- `created_by varchar(64)`
- `updated_by varchar(64)`
- `updated_at timestamp`
- `unique(account)`

`account_applications` 增加：

- `status varchar(32) not null default 'enabled'`
- `secret_version integer not null default 1`
- `created_by varchar(64)`
- `updated_by varchar(64)`
- `updated_at timestamp`

`account_user_applications` 增加：

- `status varchar(32) not null default 'enabled'`
- `authorized_by varchar(64)`
- `deauthorized_at timestamp`

新增 `account_operation_logs`：

- `id varchar(64) primary key`
- `operator_id varchar(64)`
- `operation_type varchar(64) not null`
- `target_type varchar(64) not null`
- `target_id varchar(128) not null`
- `detail text`
- `created_at timestamp not null default current_timestamp`

新增 `account_nonce_records`：

- `nonce varchar(128) primary key`
- `app_code varchar(64) not null`
- `purpose varchar(64) not null`
- `expires_at timestamp not null`
- `created_at timestamp not null default current_timestamp`

新增 `account_admin_tickets`：

- `code varchar(128) primary key`
- `app_code varchar(64) not null`
- `user_id varchar(64) not null`
- `purpose varchar(64) not null`
- `expires_at timestamp not null`
- `used_at timestamp`
- `created_at timestamp not null default current_timestamp`

### 2.4 验收

- PostgreSQL 能从空库自动迁移到最新版本。
- MySQL 使用 `--spring.profiles.active=mysql` 能从空库自动迁移到最新版本。
- `mvn test` 覆盖 H2 兼容迁移。
- 不再依赖生产启动时执行 `schema.sql`。

## 3. 用户管理完善

### 3.1 功能

- 用户列表：按账号、姓名、邮箱、手机号、状态搜索。
- 用户详情：展示基础信息、已授权应用、最近同步结果。
- 用户编辑：修改账号、邮箱、姓名、手机号。
- 用户启用/禁用：
  - 禁用 Account 用户后，默认调用所有已授权应用的 disable 接口。
  - 启用 Account 用户不自动恢复应用授权，需要管理员重新授权。
- 用户授权应用：
  - 可选择应用列表，不再手填 appCode。
  - 授权成功后显示应用同步结果。
- 用户取消授权：
  - 调用应用 disable 接口。
  - 记录取消授权操作日志。

### 3.2 待改文件

- Modify: `src/main/java/com/hypers/account/app/AccountUser.java`
- Modify: `src/main/java/com/hypers/account/app/SaveUserCommand.java`
- Modify: `src/main/java/com/hypers/account/app/AccountStore.java`
- Create: `src/main/java/com/hypers/account/app/MyBatisAccountStore.java`（替代 JdbcAccountStore）
- Create: `src/main/java/com/hypers/account/mapper/AccountUserMapper.java`
- Create: `src/main/resources/mapper/AccountUserMapper.xml`
- Modify: `src/main/java/com/hypers/account/app/InMemoryAccountStore.java`
- Modify: `src/main/java/com/hypers/account/app/AccountDirectoryService.java`
- Modify: `src/main/java/com/hypers/account/web/AccountDirectoryController.java`
- Modify: `src/main/resources/templates/users.html`
- Test: `src/test/java/com/hypers/account/app/AccountDirectoryServiceTest.java`
- Test: `src/test/java/com/hypers/account/app/MyBatisAccountStoreTest.java`（替代 JdbcAccountStoreTest）
- Test: `src/test/java/com/hypers/account/web/AccountApiIntegrationTest.java`
- Test: `src/test/java/com/hypers/account/web/HomePageTest.java`

### 3.3 新增接口

- `GET /api/users?keyword=&status=&page=&size=`
- `GET /api/users/{userId}`
- `PUT /api/users/{userId}`
- `POST /api/users/{userId}/enable`
- `POST /api/users/{userId}/disable`
- `GET /api/users/{userId}/applications`

### 3.4 验收

- 管理页面可完成用户新增、查询、编辑、禁用。
- 禁用用户后，已授权应用收到 disable 同步请求。
- 未授权应用不会收到用户编辑同步。
- 禁用用户不能生成 SSO Code。

## 4. 应用管理完善

### 4.1 功能

- 应用列表：按应用编码、名称、状态搜索。
- 应用详情：展示入口地址、回调地址、通知地址、iframe 地址、默认租户、密钥版本。
- 应用编辑：允许更新名称、入口、回调、iframe、通知地址、默认租户。
- 应用启用/禁用：
  - 禁用应用后不能授权用户。
  - 禁用应用后不能生成 SSO Code。
- 密钥轮换：
  - 管理员点击"重置密钥"生成新 secret。
  - secret 只在重置成功后显示一次。
  - `secret_version` 递增。

### 4.2 待建/待改文件

- Modify: `src/main/java/com/hypers/account/app/AccountApplication.java`
- Modify: `src/main/java/com/hypers/account/app/RegisterApplicationCommand.java`
- Modify: `src/main/java/com/hypers/account/app/AccountStore.java`
- Modify: `src/main/java/com/hypers/account/app/MyBatisAccountStore.java`
- Create: `src/main/java/com/hypers/account/mapper/AccountApplicationMapper.java`
- Create: `src/main/resources/mapper/AccountApplicationMapper.xml`
- Modify: `src/main/java/com/hypers/account/app/AccountDirectoryService.java`
- Modify: `src/main/java/com/hypers/account/web/AccountDirectoryController.java`
- Modify: `src/main/resources/templates/applications.html`
- Test: `src/test/java/com/hypers/account/app/MyBatisAccountStoreTest.java`
- Test: `src/test/java/com/hypers/account/web/AccountApiIntegrationTest.java`

### 4.3 新增接口

- `GET /api/applications?keyword=&status=&page=&size=`
- `GET /api/applications/{appCode}`
- `PUT /api/applications/{appCode}`
- `POST /api/applications/{appCode}/enable`
- `POST /api/applications/{appCode}/disable`
- `POST /api/applications/{appCode}/secret/rotate`

### 4.4 验收

- 应用页面可完成新增、查询、编辑、禁用、启用。
- 默认租户为空时保存为 `default`。
- 禁用应用不能被授权，也不能签发 SSO Code。
- 重置密钥后旧密钥调用应用开放接口失败，新密钥成功。

## 5. 应用接口同步客户端

### 5.1 功能

替换当前 `NoopApplicationUserSyncClient`，实现真实 HTTP 调用。

授权用户时调用应用：

- `POST {notifyBaseUrl}/account-sso/internal/users/upsert`

取消授权或禁用用户时调用应用：

- `POST {notifyBaseUrl}/account-sso/internal/users/disable`

### 5.2 请求体

```json
{
  "appCode": "cms-ai",
  "tenantCode": "default",
  "externalUserId": "account-user-id",
  "account": "zhangsan",
  "email": "zhangsan@example.com",
  "name": "张三",
  "phone": "13800000000",
  "enabled": true
}
```

### 5.3 签名请求头

- `X-Account-App-Code`
- `X-Account-Timestamp`
- `X-Account-Nonce`
- `X-Account-Signature`

签名原文：

```text
method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body
```

签名算法：

```text
Base64(HmacSHA256(secret, signText))
```

### 5.4 待建/待改文件

- Create: `src/main/java/com/hypers/account/app/http/HttpApplicationUserSyncClient.java`
- Create: `src/main/java/com/hypers/account/app/http/ApplicationSyncRequest.java`
- Create: `src/main/java/com/hypers/account/app/http/ApplicationSyncException.java`
- Modify: `src/main/java/com/hypers/account/config/AccountCenterConfiguration.java`
- Modify: `src/main/java/com/hypers/account/security/HmacSignatureService.java`
- Test: `src/test/java/com/hypers/account/app/http/HttpApplicationUserSyncClientTest.java`
- Test: `src/test/java/com/hypers/account/app/AccountDirectoryServiceTest.java`

### 5.5 验收

- 授权用户会调用应用 upsert。
- 取消授权会调用应用 disable。
- 用户编辑只同步已授权应用。
- 应用接口 4xx/5xx 时，Account 返回明确错误并记录日志。
- HTTP 超时不超过 5 秒。

## 6. HMAC nonce 防重放

### 6.1 功能

- Account 调用应用接口时生成 nonce。
- 应用调用 Account OpenAPI 时也必须带 nonce。
- Account 记录已使用 nonce，过期时间建议 5 分钟。
- 同一个 `appCode + nonce + purpose` 重复使用时拒绝。

### 6.2 待建/待改文件

- Create: `src/main/java/com/hypers/account/security/NonceStore.java`
- Create: `src/main/java/com/hypers/account/mapper/NonceRecordMapper.java`
- Create: `src/main/resources/mapper/NonceRecordMapper.xml`
- Create: `src/main/java/com/hypers/account/security/MyBatisNonceStore.java`
- Create: `src/main/java/com/hypers/account/security/SignatureVerifier.java`
- Modify: `src/main/java/com/hypers/account/web/SsoTicketController.java`
- Test: `src/test/java/com/hypers/account/security/SignatureVerifierTest.java`
- Test: `src/test/java/com/hypers/account/web/AccountOpenApiSecurityTest.java`

### 6.3 验收

- 缺少签名头的 OpenAPI 请求返回 401。
- timestamp 超过 5 分钟返回 401。
- nonce 重复返回 401。
- 签名错误返回 401。
- 签名正确且 nonce 首次使用时请求成功。

## 7. 标准 SSO 浏览器流程

### 7.1 功能

当前已有 API 方式生成 code。后续需要补齐用户从应用进入 Account，再回调应用的浏览器流程。

流程：

1. 用户访问应用。
2. 应用发现本地未登录，跳转：

```text
GET /sso/authorize?appCode=cms-ai&redirectUri=http://localhost:9003/account-sso/callback&state=random
```

3. Account 判断用户是否已登录 Account。
4. 未登录则进入 Account 登录页。
5. 已登录则检查用户是否授权该应用。
6. 已授权则生成一次性 code。
7. Account 重定向：

```text
http://localhost:9003/account-sso/callback?code=xxx&state=random
```

8. 应用后端调用 `/openapi/sso/tickets/exchange` 换取用户信息。
9. 应用签发自己的本地 token。

### 7.2 待建/待改文件

- Create: `src/main/java/com/hypers/account/auth/AccountSessionUser.java`
- Create: `src/main/java/com/hypers/account/auth/AccountLoginService.java`
- Create: `src/main/java/com/hypers/account/web/AuthController.java`
- Create: `src/main/java/com/hypers/account/web/SsoAuthorizeController.java`
- Create: `src/main/resources/templates/login.html`
- Modify: `src/main/java/com/hypers/account/sso/SsoTicketService.java`
- Modify: `src/main/java/com/hypers/account/web/SsoTicketController.java`
- Test: `src/test/java/com/hypers/account/web/SsoAuthorizeFlowTest.java`

### 7.3 新增页面与接口

- `GET /login`
- `POST /login`
- `POST /logout`
- `GET /sso/authorize`

### 7.4 验收

- 未登录访问 `/sso/authorize` 跳转登录页。
- 登录后回到原始 SSO 请求。
- 未授权应用显示无权限页面，不生成 code。
- 已授权应用重定向回应用 callback。
- code 一次性使用，重复兑换失败。

## 8. iframe 管理票据

### 8.1 功能

Account 打开应用 iframe 授权页面时，不再传 `preview` 字符串，而是生成短期管理票据。应用 iframe 页面加载后调用 Account 校验票据，拿到用户和应用上下文。

### 8.2 票据规则

- 有效期：60 秒。
- 一次性使用：建议校验成功后标记 used。
- 绑定字段：
  - `appCode`
  - `userId`
  - `operatorId`
  - `purpose=iframe-permission`

### 8.3 新增接口

Account 管理页面调用：

- `POST /api/admin-tickets`

应用 iframe 后端调用：

- `POST /openapi/admin-tickets/verify`

`POST /api/admin-tickets` 请求体：

```json
{
  "appCode": "cms-ai",
  "userId": "account-user-id",
  "purpose": "iframe-permission"
}
```

`POST /openapi/admin-tickets/verify` 请求体：

```json
{
  "appCode": "cms-ai",
  "ticket": "ticket-code"
}
```

### 8.4 待建/待改文件

- Create: `src/main/java/com/hypers/account/admin/AdminTicketService.java`
- Create: `src/main/java/com/hypers/account/admin/AdminTicketPayload.java`
- Create: `src/main/java/com/hypers/account/mapper/AdminTicketMapper.java`
- Create: `src/main/resources/mapper/AdminTicketMapper.xml`
- Create: `src/main/java/com/hypers/account/admin/MyBatisAdminTicketStore.java`
- Create: `src/main/java/com/hypers/account/web/AdminTicketController.java`
- Modify: `src/main/resources/templates/users.html`
- Test: `src/test/java/com/hypers/account/admin/AdminTicketServiceTest.java`
- Test: `src/test/java/com/hypers/account/web/AdminTicketControllerTest.java`

### 8.5 验收

- `/users` 点击打开 iframe 前先生成 ticket。
- iframe URL 携带真实 ticket。
- 应用校验 ticket 成功后拿到 `appCode`、`userId`、`tenantCode`。
- ticket 过期、重复使用、应用不匹配时失败。

## 9. 管理端登录与权限

### 9.1 功能

Account 管理页面需要登录保护。第一版使用 Account 本地用户作为管理员来源，不引入外部认证。参考 account-portal 的角色设计（SUPER_USER、ADMIN_USER、AUDIT_USER、STAFF_USER）。

### 9.2 角色

- `ACCOUNT_ADMIN`：可管理用户、应用、授权、密钥。
- `ACCOUNT_AUDITOR`：只读查看用户、应用、日志。

### 9.3 新增表

`account_admin_roles`：

- `user_id varchar(64) not null`
- `role_code varchar(64) not null`
- `created_at timestamp not null default current_timestamp`
- primary key: `(user_id, role_code)`

### 9.4 待建/待改文件

- Create: `src/main/java/com/hypers/account/auth/AdminRole.java`
- Create: `src/main/java/com/hypers/account/auth/AdminAuthorizationService.java`
- Create: `src/main/java/com/hypers/account/web/AdminAuthInterceptor.java`
- Create: `src/main/java/com/hypers/account/mapper/AdminRoleMapper.java`
- Create: `src/main/resources/mapper/AdminRoleMapper.xml`
- Modify: `src/main/java/com/hypers/account/web/HomeController.java`
- Modify: `src/main/java/com/hypers/account/web/AccountDirectoryController.java`
- Modify: `src/main/java/com/hypers/account/web/SsoTicketController.java`
- Test: `src/test/java/com/hypers/account/web/AdminAuthInterceptorTest.java`

### 9.5 验收

- 未登录访问 `/users`、`/applications` 跳转 `/login`。
- 普通用户不能访问管理 API。
- `ACCOUNT_AUDITOR` 不能执行新增、编辑、授权、禁用、密钥轮换。
- `ACCOUNT_ADMIN` 可执行全部管理操作。

## 10. Starter 包计划

### 10.1 目标

提供给各应用引入的标准包，减少每个系统重复写 SSO、签名、DTO 和接口骨架。

### 10.2 Maven 模块

建议把当前项目改为多模块：

- `account-center-server`：当前 Account Center 服务。
- `account-center-starter`：给业务应用引入的 starter。

### 10.3 Starter 内容

包名建议：

```text
com.hypers.account.starter
```

提供：

- DTO：
  - `AccountUserUpsertRequest`
  - `AccountUserDisableRequest`
  - `SsoTicketExchangeRequest`
  - `SsoTicketExchangeResponse`
  - `AdminTicketVerifyRequest`
  - `AdminTicketVerifyResponse`
- 签名：
  - `AccountHmacSigner`
  - `AccountHmacVerifier`
  - `AccountNonceVerifier`
- 客户端：
  - `AccountSsoClient`
  - `AccountAdminTicketClient`
- 应用侧扩展接口：
  - `AccountUserProvisionService`
  - `AccountSsoLoginHandler`
  - `AccountPermissionPageContextResolver`
- 默认 Controller：
  - `AccountSsoCallbackController`
  - `AccountInternalUserController`

### 10.4 待建/待改文件

- Modify: `pom.xml`
- Create: `account-center-server/pom.xml`
- Create: `account-center-starter/pom.xml`
- Move: `src/main/**` 到 `account-center-server/src/main/**`
- Move: `src/test/**` 到 `account-center-server/src/test/**`
- Create: `account-center-starter/src/main/java/com/hypers/account/starter/**`
- Create: `account-center-starter/src/test/java/com/hypers/account/starter/**`

### 10.5 验收

- `mvn test` 在父工程下通过。
- 业务应用引入 starter 后，只需实现 `AccountUserProvisionService` 和 `AccountSsoLoginHandler`。
- starter 不依赖 Account Center 服务端内部实现类。
- starter 暴露的 DTO 与 Account OpenAPI 字段一致。

## 11. 审计与操作日志

### 11.1 功能

记录以下事件：

- 用户新增、编辑、启用、禁用。
- 应用新增、编辑、启用、禁用、密钥轮换。
- 用户授权应用、取消授权应用。
- 应用同步成功、失败。
- SSO code 签发、兑换成功、兑换失败。
- iframe 管理 ticket 签发、校验成功、校验失败。
- 管理端登录、退出、登录失败。

参考 account-portal 的审计模式：通过 Spring ApplicationEvent 解耦审计记录，避免侵入业务代码。

### 11.2 新增接口

- `GET /api/audit-logs?operatorId=&operationType=&targetType=&targetId=&from=&to=&page=&size=`

### 11.3 待建/待改文件

- Create: `src/main/java/com/hypers/account/audit/AuditLog.java`
- Create: `src/main/java/com/hypers/account/audit/AuditLogService.java`
- Create: `src/main/java/com/hypers/account/mapper/AuditLogMapper.java`
- Create: `src/main/resources/mapper/AuditLogMapper.xml`
- Create: `src/main/java/com/hypers/account/web/AuditLogController.java`
- Modify: service/controller files that perform audited operations.
- Test: `src/test/java/com/hypers/account/audit/AuditLogServiceTest.java`
- Test: `src/test/java/com/hypers/account/web/AuditLogControllerTest.java`

### 11.4 验收

- 每个管理操作都有审计记录。
- 失败的同步和失败的票据校验也有审计记录。
- 审计查询支持分页。
- `ACCOUNT_AUDITOR` 可以查看日志，不能修改业务数据。

## 12. `cms-ai` 接入计划

### 12.1 Account Center 侧配置

应用配置建议：

```json
{
  "appCode": "cms-ai",
  "name": "双碳服务",
  "entryUrl": "http://localhost:9003",
  "ssoCallbackUrl": "http://localhost:9003/account-sso/callback",
  "permissionIframeUrl": "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
  "notifyBaseUrl": "http://localhost:9003",
  "defaultTenantCode": "default"
}
```

### 12.2 `cms-ai` 侧改造

- 引入 `account-center-starter`。
- 放行路径：
  - `/account-sso/**`
  - `/account-admin/users/*/permissions`
- 实现用户同步：
  - 已存在用户：按账号定位并更新邮箱、姓名、手机号。
  - 不存在用户：创建本地用户。
  - 默认租户：`default`。
  - 默认组织和默认角色：使用 `cms-ai` 自己配置的默认值。
- 实现 SSO 回调：
  - 使用 code 调 Account 换用户信息。
  - 根据 account 找本地用户。
  - 调用 `AuthServiceImpl.doLogin(sysUser)` 签发 `cms-ai` 自己的 JWT token。
- 实现 iframe 授权页：
  - 校验 Account 管理 ticket。
  - 复用 `cms-ai` 自己的角色、菜单、按钮、数据权限页面或接口。

### 12.3 `cms-ai` 侧集成要点

参考 `cms-ai` 现有认证架构：
- 网关层 `AccessFilter` 可以扩展支持 Account Center 的 token 校验
- `AuthServiceImpl.doLogin(SysUser)` 可用于 SSO 回调后签发本地 JWT
- `SpringSecurityConfig` 的白名单需要增加 `/account-sso/**` 路径
- 用户同步可以在 `snowy-system` 或 `custom-base` 模块中实现

### 12.4 验收

- Account 授权 `cms-ai` 后，`cms-ai` 本地创建或更新用户。
- Account 取消授权后，`cms-ai` 本地用户被禁用或解绑。
- 从 `cms-ai` 跳转 Account SSO 后能回到 `cms-ai` 并获得本地登录态。
- Account iframe 能打开 `cms-ai` 的权限配置页。
- `cms-ai` 的角色、菜单、数据权限不迁移到 Account。

## 13. 页面体验完善

### 13.1 `/users`

需要补：

- 用户列表表格。
- 搜索栏。
- 用户编辑弹窗或右侧编辑区。
- 授权应用选择器。
- 已授权应用列表。
- 同步结果展示。
- iframe ticket 自动生成。

### 13.2 `/applications`

需要补：

- 应用列表表格。
- 搜索栏。
- 应用编辑区。
- 启用/禁用按钮。
- 密钥重置按钮。
- 复制接入信息按钮。

### 13.3 `/audit-logs`

需要新增：

- 审计日志列表。
- 操作类型筛选。
- 目标对象筛选。
- 时间范围筛选。

### 13.4 验收

- 所有按钮有成功和失败状态提示。
- 页面不再要求管理员手动复制用户 ID 才能授权。
- iframe 加载失败时有明确提示。
- 移动端最小宽度下文本不重叠。

## 14. 测试计划

### 14.1 单元测试

- `AccountDirectoryServiceTest`
  - 用户编辑只同步已授权应用。
  - 禁用用户同步所有已授权应用。
  - 禁用应用不能授权。
- `SsoTicketServiceTest`
  - code 过期失败。
  - code 重复兑换失败。
  - appCode 不匹配失败。
- `AdminTicketServiceTest`
  - ticket 过期失败。
  - ticket 重复使用失败。
  - appCode 不匹配失败。
- `SignatureVerifierTest`
  - 签名正确成功。
  - 签名错误失败。
  - timestamp 超时失败。
  - nonce 重复失败。

### 14.2 存储测试

- `MyBatisAccountStoreTest`
  - 用户 CRUD。
  - 应用 CRUD。
  - 授权关系 CRUD。
  - 状态字段持久化。
  - 默认租户为空时落库为 `default`。

### 14.3 Web/API 测试

- `AccountApiIntegrationTest`
  - 用户新增、查询、编辑、禁用。
  - 应用新增、查询、编辑、禁用、密钥轮换。
  - 授权、取消授权。
  - SSO code 签发与兑换。
- `SsoAuthorizeFlowTest`
  - 未登录跳登录。
  - 已登录已授权回调应用。
  - 已登录未授权显示无权限。
- `AdminTicketControllerTest`
  - 管理票据签发与校验。
- `HomePageTest`
  - `/users`、`/applications`、`/audit-logs` 页面可访问。

### 14.4 集成验证命令

每个阶段完成后运行：

```powershell
mvn test
```

打包验证：

```powershell
mvn package -DskipTests
```

PostgreSQL 启动验证：

```powershell
$env:ACCOUNT_DB_URL="jdbc:postgresql://localhost:5432/account_center"
$env:ACCOUNT_DB_USERNAME="account"
$env:ACCOUNT_DB_PASSWORD="account"
java -jar target\account-center-0.1.0-SNAPSHOT.jar
```

MySQL 启动验证：

```powershell
$env:ACCOUNT_DB_URL="jdbc:mysql://localhost:3306/account_center?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:ACCOUNT_DB_USERNAME="account"
$env:ACCOUNT_DB_PASSWORD="account"
java -jar target\account-center-0.1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```

## 15. 推荐实施顺序

### Phase 1：JDBC -> MyBatis 迁移 + 数据基础

- [x] 将 `spring-boot-starter-jdbc` 替换为 `mybatis-spring-boot-starter`。
- [x] 创建 MyBatis Mapper 接口和 XML 映射文件。
- [x] 将 `JdbcAccountStore` 重构为 `MyBatisAccountStore`。
- [ ] 引入 Flyway。
- [ ] 把 `schema.sql` 迁移为 `V1__init_account_center.sql`。
- [ ] 增加状态、审计、nonce、admin ticket 相关表。
- [ ] 补 MyBatis 存储测试。
- [ ] 跑 `mvn test`。

### Phase 2：用户和应用 CRUD

- [ ] 完善用户字段和状态。
- [ ] 实现用户查询、编辑、启用、禁用 API。
- [ ] 完善应用字段和状态。
- [ ] 实现应用查询、编辑、启用、禁用、密钥轮换 API。
- [ ] 改造 `/users` 和 `/applications` 页面。
- [ ] 跑 `mvn test`。

### Phase 3：真实应用同步

- [ ] 实现 HTTP 同步客户端。
- [ ] 实现 HMAC 请求签名。
- [ ] 实现同步失败错误模型。
- [ ] 授权、取消授权、用户编辑接入真实同步。
- [ ] 跑 `mvn test`。

### Phase 4：SSO 浏览器流程

- [ ] 实现 Account 登录页和登录态。
- [ ] 实现 `/sso/authorize`。
- [ ] 实现未授权提示页。
- [ ] 强化 `/openapi/sso/tickets/exchange` 签名校验。
- [ ] 跑 `mvn test`。

### Phase 5：iframe 管理票据

- [ ] 实现 admin ticket 存储。
- [ ] 实现 ticket 签发接口。
- [ ] 实现 ticket 校验 OpenAPI。
- [ ] 改造 `/users` 页面 iframe 打开逻辑。
- [ ] 跑 `mvn test`。

### Phase 6：Starter 包

- [ ] 改成 Maven 多模块。
- [ ] 抽 DTO 到 starter。
- [ ] 抽签名工具到 starter。
- [ ] 实现 SSO 兑换客户端。
- [ ] 实现应用侧默认 Controller 骨架。
- [ ] 跑父工程 `mvn test`。

### Phase 7：审计与权限

- [ ] 实现审计日志服务。
- [ ] 接入所有管理操作。
- [ ] 实现 `ACCOUNT_ADMIN` 和 `ACCOUNT_AUDITOR`。
- [ ] 新增 `/audit-logs` 页面。
- [ ] 跑 `mvn test`。

### Phase 8：`cms-ai` 接入

- [ ] 在 Account 中配置 `cms-ai` 应用。
- [ ] 在 `cms-ai` 引入 starter。
- [ ] 实现 `cms-ai` 用户 upsert/disable。
- [ ] 实现 `cms-ai` SSO callback。
- [ ] 实现或复用 `cms-ai` iframe 授权页。
- [ ] 做端到端联调。

## 16. 风险与决策

- 不把应用角色、菜单、按钮、数据权限搬到 Account。Account 只统一准入和 SSO。
- 无租户应用统一使用 `default`，应用侧可忽略租户含义。
- 第一版只保留接口通知，不做 MQ。
- Starter 包应保持轻量，不依赖 Account Center 服务端数据库和页面代码。
- PostgreSQL 是默认生产库；MySQL profile 需要在每次迁移后做一次真实库验证。
- MyBatis XML 映射保持 SQL 与 Java 代码分离，方便 DBA 审核和调优。
- 参考 account-portal 的领域模型继承体系，但保持精简，不过度抽象。

## 17. 完成标准

- 所有 Phase 1 到 Phase 7 完成后，Account Center 可以作为独立账户中心部署。
- `cms-ai` 完成 Phase 8 后，双碳服务可以通过 Account Center 完成 SSO、用户同步和 iframe 权限配置。
- `mvn test` 通过。
- PostgreSQL 和 MySQL 至少各完成一次空库启动迁移验证。
- 管理端页面能完成用户、应用、授权、审计的常用操作。
- 应用接入文档能让新应用按 starter 接入，不需要阅读 Account Center 服务端源码。
