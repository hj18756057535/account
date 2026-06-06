# Account Center 架构设计文档

## 1. 系统定位

Account Center 是内部统一账户中心，负责用户身份管理、应用准入授权和 SSO 单点登录。**Account Center 只做准入控制**，各业务应用继续维护自己的角色、菜单、按钮和数据权限。

## 2. 技术栈

| 组件 | 技术选型 | 版本 |
|------|---------|------|
| 框架 | Spring Boot | 2.7.13 |
| JDK | Java | 11 |
| ORM | MyBatis | 2.3.2 (starter) |
| 迁移 | Flyway | 8.5.x |
| 模板 | Thymeleaf | - |
| 数据库 | PostgreSQL (主) / MySQL (备) / H2 (测试) | - |
| 密码 | BCrypt (spring-security-crypto) | - |
| 构建 | Maven 多模块 | - |

## 3. 模块结构

```
account-center/                          # 父 POM (packaging: pom)
├── account-center-server/               # 服务端，Spring Boot 可执行 jar
│   ├── pom.xml                          # 依赖 server + starter
│   └── src/main/resources/              # 额外配置和模板
├── account-center-starter/              # SDK，给业务应用引入
│   ├── pom.xml                          # 仅依赖 jackson-databind
│   └── src/main/java/                   # DTO、签名、客户端、接口
└── src/                                 # 服务端主源码（通过 build-helper 共享）
    ├── main/java/com/hypers/account/    # Java 源码
    ├── main/resources/                  # 配置、迁移、Mapper XML、模板
    └── test/                            # 测试
```

**设计决策：** starter 模块不依赖 Spring，保持纯 Java SDK，业务应用引入后不会引入额外的 Spring Bean 或自动配置。

## 4. 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│  Thymeleaf 页面  │  REST API (/api/)  │  OpenAPI (/openapi/) │
├─────────────────────────────────────────────────────────────┤
│                    Controller 层 (web)                        │
│  AccountDirectoryController │ SsoTicketController            │
│  AuthController │ AdminTicketController │ AuditLogController │
├─────────────────────────────────────────────────────────────┤
│                    Service 层                                 │
│  AccountDirectoryService │ SsoTicketService                  │
│  AccountLoginService │ AdminTicketService │ AuditLogService  │
├─────────────────────────────────────────────────────────────┤
│                    Store 层 (数据访问抽象)                      │
│  AccountStore (接口)                                          │
│  ├── MyBatisAccountStore (生产)                               │
│  └── InMemoryAccountStore (测试)                              │
├─────────────────────────────────────────────────────────────┤
│                    Mapper 层 (MyBatis)                        │
│  AccountUserMapper │ AccountApplicationMapper                │
│  AccountUserApplicationMapper │ AdminTicketMapper            │
│  AuditLogMapper │ AdminRoleMapper                            │
├─────────────────────────────────────────────────────────────┤
│                    数据库                                      │
│  PostgreSQL / MySQL / H2                                      │
└─────────────────────────────────────────────────────────────┘
```

### 层间调用规则

- **Controller** 只做参数校验和请求路由，不包含业务逻辑，**不直接调用 Mapper**
- **Service** 编排业务逻辑，调用 Store 和同步客户端
- **Store** 封装持久化细节，Service 只依赖 Store 接口
- **Mapper** 薄层 MyBatis 接口，只被 Store 实现类调用

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
POST\n/account-sso/internal/users/upsert\n1710000000000\nabc123def456\n{"appCode":"cms-ai",...}
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
| `account.store.type` | mybatis | 存储实现：mybatis / memory |
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

| 异常类型 | HTTP 状态 | 触发场景 |
|---|---|---|
| `IllegalArgumentException` | 400 | 业务校验失败（用户/应用不存在、已禁用等） |
| `SsoTicketException` | 400 | SSO ticket 错误（未找到、已使用、过期） |
| `ApplicationSyncException` | 502 | 应用同步失败（应用返回非 2xx 或网络异常） |

统一响应格式：

```json
{
  "error": "错误信息"
}
```
