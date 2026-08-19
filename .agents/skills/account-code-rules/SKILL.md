---
name: account-code-rules
description: 在 Account Center 仓库中生成或修改 Java 代码时使用，包括领域对象、Command、DTO、Service、Store、Controller、Mapper、SSO、安全组件和业务逻辑。
---

# Account Center Java 代码规则

本 Skill 是仓库 Java 代码修改入口。读取后先判断包归属和既有模式，再按下列清单执行。详细架构以根目录 `AGENTS.md` 为准。

## 工作流程

1. 先搜索同包相似实现，确认 Controller、Service、Store、Mapper、Entity 的命名和既有模式。
2. 判断改动属于用户管理、应用管理、授权管理、SSO、安全签名还是管理后台 UI。
3. 优先复用现有工具类、异常处理和已有的 Store 方法。
4. 集合输入先批量查库，再用 `Map` / `groupingBy` 组装；看到循环查库要优先改成批量查询。
5. 代码改完后执行受影响模块编译或说明无法编译的原因。

需要更具体规则时：

- 数据库、SQL、Flyway 迁移、Mapper XML 变更：使用 `.agents/skills/account-database-rules`。
- 慢查询、N+1、循环查库、批量查询优化：使用 `.agents/skills/account-query-performance`。

## 项目分层

```
Controller (web)  -->  AccountDirectoryService / SsoTicketService  -->  AccountStore (接口)
                                                                          |-- MyBatisAccountStore
                                                                          |-- InMemoryAccountStore
                                                                                |
                                                                          Mapper (mapper)
                                                                                |
                                                                          Mapper XML (resources/mapper)
```

- **Controller**: 只做参数校验和请求路由，不包含业务逻辑，不直接调用 Mapper。
- **Service** (`AccountDirectoryService`): 编排业务逻辑，调用 Store 和同步客户端。
- **Store** (`AccountStore`): 数据访问抽象层，封装持久化细节。Service 只依赖 Store 接口。
- **Mapper**: 薄层 MyBatis 接口，只被 Store 实现类调用。
- **Domain Entity** (`AccountUser`, `AccountApplication`): 纯 POJO，不使用 ORM 注解。

## 必须遵守

| 主题 | 规则 |
| --- | --- |
| 注入 | 使用构造器注入，不使用 `@Autowired` 或 `@Resource` 注解。 |
| 分层 | Controller 不直接调用 Mapper；业务逻辑写在 Service/Store；Store 实现调用 Mapper。 |
| 返回 | `List` 返回空集合，不返回 `null`。单条查询找不到时抛 `IllegalArgumentException`。 |
| 异常 | 业务异常统一抛 `IllegalArgumentException`，由 `AccountExceptionHandler` 全局处理返回 400。SSO 异常用 `SsoTicketException`。 |
| 领域实体 | `AccountUser`、`AccountApplication` 是纯 POJO，不加 `@TableName`、`@TableField` 等注解。 |
| Mapper | 接口使用 `@Mapper` 注解。SQL 定义在 `src/main/resources/mapper/*.xml`，不使用注解 SQL。 |
| ResultMap | XML 中的 `resultMap` 必须完整映射所有查询字段到 Java 属性，使用下划线转驼峰或显式 `<result>` 映射。 |
| 模糊查询 | XML 中使用 `concat('%', #{keyword}, '%')`，不直接拼接 `%` 到 Java 代码。 |
| DTO / Command | 命令对象（Command）封装业务入参，Controller 内部类做参数校验（`@Valid` + `@NotBlank` 等）。 |
| 校验 | Controller Request DTO 使用 `javax.validation` 注解校验；`@Valid` 触发校验。 |
| 密钥 | `AccountApplication.secret` 是敏感字段，日志和 API 响应中不得原样暴露。 |
| 注释 | 简单代码不写废话注释；复杂业务规则、跨表组装、安全相关逻辑写简短功能注释。 |
| UUID | 用户 ID 使用 `UUID.randomUUID().toString()`，不加 `-` 分隔符。 |

## Store 模式

本项目使用 Store 模式替代传统 Repository 模式：

```java
// 接口定义数据访问契约
public interface AccountStore {
    AccountUser saveNewUser(SaveUserCommand command);
    AccountUser requireUser(String userId);
    List<AccountUser> findUsers(String keyword, String status);
    // ...
}

// MyBatis 实现通过 Mapper 操作数据库
public class MyBatisAccountStore implements AccountStore {
    private final AccountUserMapper userMapper;
    // 构造器注入
}
```

- 新增数据访问方法先加到 `AccountStore` 接口，再在 `MyBatisAccountStore` 和 `InMemoryAccountStore` 同步实现。
- Store 实现类不包含业务编排逻辑，只做数据转换和持久化。
- `InMemoryAccountStore` 和 `MyBatisAccountStore` 行为必须一致，不能只改一个。

## Mapper XML 模式

```xml
<resultMap id="userResultMap" type="com.hypers.account.app.AccountUser">
    <id property="id" column="id"/>
    <result property="account" column="account"/>
    <!-- 所有查询字段必须映射 -->
</resultMap>

<select id="selectByKeyword" resultMap="userResultMap">
    select id, account, email, name, phone, status, created_by, updated_by, created_at, updated_at
    from account_users
    <where>
        <if test="status != null and status != ''">
            and status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
            and (account like concat('%', #{keyword}, '%')
                or name like concat('%', #{keyword}, '%'))
        </if>
    </where>
    order by created_at desc
</select>
```

关键点：
- `<where>` 标签自动处理 `AND` 前缀。
- `<if>` 标签做条件过滤，避免查全部数据。
- 参数使用 `#{}` 占位符，不使用 `${}` 拼接。
- 模糊查询用 `concat('%', #{keyword}, '%')`。
- 查询字段全部显式列出，不使用 `SELECT *`。

## 命令对象与请求 DTO

- **Command** (`SaveUserCommand`, `RegisterApplicationCommand`): 业务层入参封装，放在 `app` 包，不含校验注解。
- **Request DTO** (Controller 内部类): 网络层入参校验，放在 `web` 包的 Controller 内部，使用 `javax.validation` 注解。
- Controller 将 Request DTO 转换为 Command 后传给 Service。

```java
// Controller 内部类 - 负责校验
public static class SaveUserRequest {
    @NotBlank private String account;
    @NotBlank private String email;
    @NotBlank private String name;
    @NotBlank private String phone;
    // getter/setter
}

// Command - 业务层入参
public class SaveUserCommand {
    private final String account;
    private final String email;
    private final String name;
    private final String phone;
    // 构造器、getter
}
```

## 全局异常处理

`AccountExceptionHandler` 统一处理异常：

| 异常类型 | HTTP 状态 | 说明 |
| --- | --- | --- |
| `IllegalArgumentException` | 400 | 通用业务错误（找不到用户/应用、应用已禁用等） |
| `SsoTicketException` | 400 | SSO ticket 相关错误（未找到、已使用、过期、应用不匹配） |

新增业务异常时优先使用 `IllegalArgumentException`，如需区分 HTTP 状态码再新增异常类型和对应的 `@ExceptionHandler`。

## 工具类优先级

| 场景 | 优先使用 |
| --- | --- |
| UUID 生成 | `java.util.UUID.randomUUID().toString()` |
| 时间 | `java.time.*`（项目使用 Clock 注入，便于测试） |
| 集合判空 | `java.util.Collections.isEmpty()` |
| 字符串判空 | `java.util.Optional` + `filter` |

不要在业务类中重复写私有工具方法。

## 测试

- 测试使用 H2 内存数据库，通过 `account.store.type=memory` 或 Flyway + H2。
- Spring Boot Test 使用 `@SpringBootTest` + `@AutoConfigureMockMvc`。
- JUnit 5 是默认测试框架（`spring-boot-starter-test` 自带）。
- Controller 测试验证 HTTP 状态码、响应体结构和错误处理。
- Store 测试验证数据持久化和查询逻辑。
- Service 测试验证业务编排逻辑（可用 `InMemoryAccountStore` 简化）。

## 验证清单

完成 Java 改动前检查：

- [ ] 包归属正确，没有跨包违规调用。
- [ ] Controller、Service/Store、Mapper 分层正确。
- [ ] 构造器注入，无 `@Autowired` / `@Resource`。
- [ ] `InMemoryAccountStore` 和 `MyBatisAccountStore` 行为一致。
- [ ] Mapper XML `resultMap` 完整映射所有字段。
- [ ] List 返回空集合，无 null。
- [ ] 模糊查询使用 `concat`，无 `%` 拼接到 Java 代码。
- [ ] 密钥等敏感信息未暴露在日志或响应中。
- [ ] 复杂逻辑有必要的功能注释。
- [ ] 执行受影响模块构建；若需跑测试，使用 `mvn test`。

