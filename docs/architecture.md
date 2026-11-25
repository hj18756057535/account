# Account Center 架构设计文档

## 1. 系统定位

Account Center 是内部统一账户中心，负责用户身份管理、应用准入授权和 SSO 单点登录。**Account Center 只做准入控制**，各业务应用继续维护自己的角色、菜单、按钮和数据权限。

## 2. 技术栈

| 组件 | 技术选型 | 版本 |
|------|---------|------|
| 框架 | Spring Boot | 3.5.16 |
| JDK | Java | 21 LTS |
| ORM | MyBatis | 3.0.5 (starter) |
| 迁移 | Flyway | 11.7.2（由 Boot BOM 管理） |
| 模板 | Thymeleaf | - |
| 数据库 | PostgreSQL (主) / MySQL (备) / H2 (测试) | - |
| 密码 | BCrypt (spring-security-crypto) | - |
| 构建 | Maven 多模块 | - |

## 3. 模块结构

```
account-center/                          # 父 POM (packaging: pom)
├── account-center-contract/             # 双向请求/响应类型与协议常量
│   └── pom.xml                          # 无 Spring、HTTP 或数据库依赖
├── account-center-server/               # 服务端，Spring Boot 可执行 jar
│   ├── pom.xml                          # 只依赖 contract，不依赖 starter
│   └── src/                             # Server 的 Java、资源与测试唯一事实源
├── account-center-spring-boot-starter/  # 业务应用双向集成制品
│   ├── pom.xml                          # 依赖 contract 与 Boot 自动配置能力
│   └── src/main/java/                   # Client、SPI、自动配置、Provider Web 与安全边界
└── pom.xml                              # Reactor 聚合与统一依赖管理
```

**设计决策：** 当前消费者均为 Spring Boot 应用，因此 Client 与自动配置合并在一个 Starter 制品中。Starter 内聚出站 Client 和入站 Provider SPI；Server 与 Starter 只共享 Contract，不共享领域实现。

## 4. 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│  Thymeleaf 页面  │  REST API (/api/)  │  OpenAPI (/openapi/) │
├─────────────────────────────────────────────────────────────┤
│                    Controller 层 (web)                        │
│  ManagementApplicationController │ SsoTicketController       │
│  AuthController │ AdminTicketController │ AuditLogController │
├─────────────────────────────────────────────────────────────┤
│                    Service 层                                 │
│  UserManagementService │ ApplicationManagementService         │
│  UserApplicationAccessService │ SsoTicketService              │
│  AccountLoginService │ AdminTicketService │ AuditLogService  │
├─────────────────────────────────────────────────────────────┤
│                    Mapper 层 (MyBatis，持久化边界)              │
│  AccountUserMapper │ AccountApplicationMapper                │
│  AccountUserApplicationMapper │ SyncCommandMapper            │
│  ApplicationSyncMapper │ DirectoryImportMapper               │
│  UserImportMapper │ AdminTicketMapper                        │
│  AuditLogMapper │ AdminRoleMapper                            │
│  └── SQL 位于 resources/mapper/*.xml                          │
├─────────────────────────────────────────────────────────────┤
│                    数据库                                      │
│  PostgreSQL / MySQL / H2                                      │
└─────────────────────────────────────────────────────────────┘
```

### 层间调用规则

- **Controller** 只做参数校验和请求路由，不包含业务逻辑，**不直接调用 Mapper**
- **Service** 编排业务规则、构建实体、判断乐观锁并抛出带消息键的业务异常，按需注入 Mapper
- **Mapper** 薄层 MyBatis 接口，只做数据访问，不承载业务判断
- 不保留 Store / Repository 中间层；测试连 H2 执行真实 SQL

## 5. 核心流程

### 5.1 SSO 单点登录流程

```
┌──────┐        ┌──────────┐        ┌───────────────┐
│ 用户  │        │ 业务应用   │        │ Account Center │
└──┬───┘        └────┬─────┘        └───────┬───────┘
   │ 1. 访问应用      │                      │
   │─────────────────>│                      │
   │                  │ 2. 发现未登录          │
   │                  │ 3. 302 跳转           │
   │                  │─────────────────────>│
   │                  │    /sso/authorize     │
   │                  │    ?appCode=xxx       │
   │                  │    &redirectUri=xxx   │
   │ 4. 显示登录页     │                      │
   │<─────────────────│──────────────────────│
   │ 5. 输入账号密码   │                      │
   │─────────────────────────────────────────>│
   │ 6. 登录成功       │                      │
   │ 7. 检查授权       │                      │
   │ 8. 生成 code      │                      │
   │ 9. 302 重定向     │                      │
   │<─────────────────│──────────────────────│
   │                  │    callback?code=xxx  │
   │                  │ 10. 后端用 code 兑换   │
   │                  │     POST /openapi/    │
   │                  │     sso/tickets/      │
   │                  │     exchange          │
   │                  │─────────────────────>│
   │                  │ 11. 返回用户信息       │
   │                  │<─────────────────────│
   │                  │ 12. 创建/匹配本地用户  │
   │                  │ 13. 签发本地 token     │
   │ 14. 登录成功      │                      │
   │<─────────────────│                      │
```

### 5.2 用户同步流程

```
Account Center                    业务应用
    │                                │
    │  管理员授权用户访问应用           │
    │  POST /account-sso/            │
    │  internal/users/upsert         │
    │  (HMAC 签名)                   │
    │───────────────────────────────>│
    │                                │  创建/更新本地用户
    │                                │
    │  管理员取消授权或禁用用户         │
    │  POST /account-sso/            │
    │  internal/users/disable        │
    │  (HMAC 签名)                   │
    │───────────────────────────────>│
    │                                │  禁用本地用户
```

### 5.3 iframe 授权页面流程

```
Account Center 管理页面              业务应用 iframe
    │                                │
    │  签发 admin ticket (60s)        │
    │  POST /api/admin-tickets       │
    │                                │
    │  打开 iframe                    │
    │  ?ticket=xxx                   │
    │───────────────────────────────>│
    │                                │  校验 ticket
    │                                │  POST /openapi/
    │                                │  admin-tickets/verify
    │                                │──────────────>│ Account Center
    │                                │  返回 userId   │
    │                                │<──────────────│
    │                                │  渲染权限配置页  │
```

## 6. 安全设计

### 6.1 HMAC 签名

所有 Account Center 到应用的内部调用以及应用到 Account Center OpenAPI 的调用，都使用 HMAC-SHA256 签名保护。

**签名请求头：**

| 头 | 说明 |
|---|---|
| `X-Account-App-Code` | 应用编码 |
| `X-Account-Timestamp` | 请求时间戳（毫秒） |
| `X-Account-Nonce` | 一次性随机数（防重放） |
| `X-Account-Signature` | Base64(HmacSHA256(secret, signText)) |

**签名原文格式：**

```
PUT\n/account-integration/users/user-1\n1710000000000\nabc123def456\n{"appCode":"cms-ai",...}
```

### 6.2 Nonce 防重放

Account Center 记录已使用的 nonce，5 分钟过期。同一 `appCode + nonce` 重复使用时拒绝。

### 6.3 SSO Code

- 一次性使用，兑换后立即失效
- 60 秒过期
- 绑定 appCode，不匹配时拒绝

### 6.4 Admin Ticket

- 60 秒有效期
- 一次性使用（校验后标记 used_at）
- 绑定 appCode + userId + purpose

## 7. 数据库设计

### 7.1 ER 图

```
account_users (1) ──── (N) account_user_applications (N) ──── (1) account_applications
      │                                                          │
      │                                                          │
      ├── account_admin_roles                                    │
      ├── account_operation_logs                                 │
      └── account_admin_tickets ─────────────────────────────────┘
```

### 7.2 表清单

| 表 | 主键 | 用途 |
|---|---|---|
| `account_users` | `id` (UUID) | 用户主表 |
| `account_applications` | `app_code` | 应用主表 |
| `account_user_applications` | `(user_id, app_code)` | 授权关联 |
| `account_operation_logs` | `id` | 审计日志 |
| `account_nonce_records` | `nonce` | 防重放记录 |
| `account_admin_tickets` | `code` | 管理 ticket |
| `account_admin_roles` | `(user_id, role_code)` | 管理员角色 |

### 7.3 关键字段

- 所有表包含审计字段：`created_by`、`updated_by`、`created_at`、`updated_at`
- 状态字段统一使用 `varchar(32)`，值为 `enabled` / `disabled`
- 用户主键使用 UUID 字符串 `varchar(64)`
- 应用主键使用业务编码 `app_code varchar(64)`

## 8. 配置项

| 配置键 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 8088 | 服务端口 |
| `account.sync.type` | http | 同步方式：http / noop |
| `spring.flyway.enabled` | true | 自动迁移 |
| `mybatis.mapper-locations` | classpath:mapper/*.xml | Mapper XML 位置 |
| `mybatis.configuration.map-underscore-to-camel-case` | true | 下划线转驼峰 |

**环境变量：**

| 变量 | 说明 |
|---|---|
| `ACCOUNT_DB_URL` | 数据库连接 URL |
| `ACCOUNT_DB_USERNAME` | 数据库用户名 |
| `ACCOUNT_DB_PASSWORD` | 数据库密码 |

## 9. 异常处理

| 类型 | 说明 |
|---|---|
| `ErrorCode` | 枚举定义 HTTP 状态、对外错误码和 i18n 消息键，是异常契约的唯一来源 |
| `BusinessException` | 业务错误的唯一异常类型，可携带字段级消息键 |
| `ApplicationSyncFailure` | 同步重试控制流（内部使用），对外统一由 `BusinessException` 返回 |

统一响应格式：

```json
{
  "error": "错误信息"
}
```
