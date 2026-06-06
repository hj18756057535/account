# Account Center

内部统一账户中心，提供用户管理、应用准入授权和 SSO 单点登录能力。

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

- JDK 11
- Maven 3.6+
- PostgreSQL 12+（或 MySQL 8.0+）

### 构建

```bash
mvn clean package -DskipTests
```

### 启动（PostgreSQL）

```bash
export ACCOUNT_DB_URL=jdbc:postgresql://localhost:5432/account_center
export ACCOUNT_DB_USERNAME=account
export ACCOUNT_DB_PASSWORD=account

java -jar account-center-server/target/account-center-server-0.1.0-SNAPSHOT.jar
```

### 启动（MySQL）

```bash
export ACCOUNT_DB_URL=jdbc:mysql://localhost:3306/account_center?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
export ACCOUNT_DB_USERNAME=account
export ACCOUNT_DB_PASSWORD=account

java -jar account-center-server/target/account-center-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=mysql
```

### 运行测试

```bash
mvn test
```

测试使用 H2 内存数据库，无需外部依赖。

## 项目结构

```
account-center/
├── pom.xml                              # 父 POM
├── account-center-server/               # 服务端模块
├── account-center-starter/              # SDK（业务应用引入）
├── docs/
│   ├── architecture.md                  # 架构设计文档
│   └── integration-guide.md             # 应用对接指南
└── src/
    ├── main/java/com/hypers/account/
    │   ├── app/                         # 领域模型、Store、Service
    │   ├── auth/                        # 登录认证
    │   ├── admin/                       # 管理 ticket
    │   ├── audit/                       # 审计日志
    │   ├── mapper/                      # MyBatis Mapper 接口
    │   ├── security/                    # HMAC 签名
    │   ├── sso/                         # SSO ticket
    │   ├── web/                         # Controller
    │   └── config/                      # Bean 配置
    ├── main/resources/
    │   ├── application.yml              # 主配置
    │   ├── application-mysql.yml        # MySQL 配置
    │   ├── db/migration/                # Flyway 迁移脚本
    │   ├── mapper/                      # MyBatis XML
    │   └── templates/                   # Thymeleaf 页面
    └── test/                            # 测试
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
| 应用 | GET | `/api/applications?keyword=&status=` | 搜索应用 |
| 应用 | GET/PUT | `/api/applications/{code}` | 查看/编辑 |
| 应用 | POST | `/api/applications/{code}/secret/rotate` | 密钥轮换 |
| 授权 | POST | `/api/users/{id}/applications/{code}/authorize` | 授权 |
| 授权 | POST | `/api/users/{id}/applications/{code}/deauthorize` | 取消授权 |
| SSO | GET | `/sso/authorize` | SSO 浏览器流程 |
| SSO | POST | `/openapi/sso/tickets/exchange` | 兑换 code |
| Ticket | POST | `/openapi/admin-tickets/verify` | 校验管理 ticket |
| 审计 | GET | `/api/audit-logs` | 查询审计日志 |

完整 API 文档见 [docs/architecture.md](docs/architecture.md)。

## 应用接入

业务应用引入 `account-center-starter` 即可接入 Account Center。只需实现两个接口：

```xml
<dependency>
    <groupId>com.hypers</groupId>
    <artifactId>account-center-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

详细对接步骤见 [docs/integration-guide.md](docs/integration-guide.md)。

## 管理后台

访问 `http://localhost:8088` 进入管理后台：

- `/` — 首页导航
- `/users` — 用户管理与授权
- `/applications` — 应用管理
- `/audit-logs` — 审计日志
- `/login` — 登录

## 技术决策

| 决策 | 说明 |
|------|------|
| 不引入 Spring Security | 仅使用 `spring-security-crypto` 做 BCrypt，避免自动配置干扰 |
| MyBatis 而非 JPA | SQL 与 Java 分离，方便 DBA 审核和调优 |
| Flyway 迁移 | 版本化管理数据库变更，支持多环境自动迁移 |
| Store 模式 | 数据访问抽象层，测试时可切换为 InMemoryAccountStore |
| Starter 不依赖 Spring | 纯 Java SDK，业务应用引入后不引入额外 Bean |
| 同步使用 HTTP | 第一版只做接口通知，不做 MQ |
