---
name: account-code-rules
description: 在 Account Center 仓库中生成或修改 Java 代码时使用，包括领域对象、Command、DTO、Service、Controller、Mapper、SSO、安全组件和业务逻辑。
---

# Account Center Java 代码规则

本 Skill 是仓库 Java 代码修改入口。读取后先判断包归属和既有模式，再按下列清单执行。详细架构以根目录 `AGENTS.md` 为准。

## 工作流程

1. 先搜索同包相似实现，确认 Controller、Service、Mapper、Entity 的命名和既有模式。
2. 判断改动属于用户管理、应用管理、授权管理、SSO、安全签名还是管理后台 UI。
3. 优先复用现有工具类、异常处理和已有的 Mapper 语句。
4. 集合输入先批量查库，再用 `Map` / `groupingBy` 组装；看到循环查库要优先改成批量查询。
5. 新增或修改 Java DTO、Command、值对象和 Spring 组件时使用 Lombok 消除样板代码；只改当前任务触及的类，不为统一风格批量重写历史代码。
6. 涉及建表、新增/修改字段或迁移时，必须同时使用 `account-database-rules`；交付包含中文表/字段说明的 SQL，并在写完脚本后执行其注释检查，缺失注释不得作为完成交付。不能用 Java 字段注释替代 SQL 注释。
7. 代码改完后执行受影响模块编译或说明无法编译的原因。

需要更具体规则时：

- 数据库、SQL、Flyway 迁移、Mapper XML 变更：使用 `.agents/skills/account-database-rules`。
- 慢查询、N+1、循环查库、批量查询优化：使用 `.agents/skills/account-query-performance`。

## 项目分层

按业务域分包（`app`、`oidc`、`sso`、`audit`、`admin`、`web`），域内再分层；不要回到顶层 `controller/service/dao` 的大平铺结构。

```
com.hypers.account
├── common/                       跨域通用模型（PageResult）
├── app/                          用户与应用业务域
│   ├── command/                  入参命令与查询对象（SaveUserCommand、UserPageQuery…）
│   ├── model/                    领域实体与值对象（AccountUser、AccountApplication、ApplicationAccess…）
│   ├── exception/                领域异常
│   ├── service/                  应用服务（UserManagementService、ApplicationManagementService、
│   │                             UserApplicationAccessService、ApplicationSynchronizationService…）
│   ├── http/                     下游 Provider 的 HTTP 适配
│   └── directory/                目录导入
├── mapper/                       持久化边界：MyBatis 接口 + resources/mapper/*.xml
├── web/                          表现层：Controller、Filter、Advice
│   └── management/               管理后台 Controller + web 支撑
│       └── service/              管理后台应用服务（写编排、幂等、导入、概览）
└── resources/mapper/*.xml
```

- **Controller**：只做参数校验和请求路由，不包含业务逻辑，不直接调用 Mapper。
- **Service**：承载业务规则、跨表组装和异常翻译，按需注入对应 Mapper；不要新增跨聚合的“万能服务”。
- **Mapper**：薄层 MyBatis 接口，SQL 在 XML；只做数据访问，不承载业务判断。
- **Domain Entity**（`AccountUser`, `AccountApplication`）：纯 POJO，不使用 ORM 注解。

## 必须遵守

| 主题 | 规则 |
| --- | --- |
| 注入 | 使用构造器注入，不使用 `@Autowired` 或 `@Resource` 注解。 |
| Lombok | Java 新增/修改代码使用 Lombok。值对象优先 `@Value`，可变 Request DTO 使用 `@Getter` + `@Setter`，Spring 组件的 `final` 依赖可使用 `@RequiredArgsConstructor`；避免无差别使用 `@Data`。 |
| 版本命名 | 默认通过 Git、制品和发布版本迭代，不在普通 URL、OpenAPI 文件名、配置键、包、Controller、DTO、异常或测试类名中携带 `v1`、`V1` 等版本号。URL 直接按业务资源组织，例如 `/api/users`。只有已发布的不兼容协议必须并行且无法增量演进时，才可建立显式兼容入口，并记录消费者、迁移窗口和退出条件。 |
| 分层 | Controller 不直接调用 Mapper；业务逻辑写在 Service；Mapper 只做数据访问。 |
| 返回 | `List` 返回空集合，不返回 `null`。单条查询找不到时抛带消息键的 `BusinessException`。 |
| 异常 | 业务异常统一抛 `BusinessException(ErrorCode)`，状态、错误码和 i18n 消息键集中在枚举；由全局处理器按请求语言翻译。 |
| 领域实体 | `AccountUser`、`AccountApplication` 是纯 POJO，不加 `@TableName`、`@TableField` 等注解。 |
| Mapper | 接口使用 `@Mapper` 注解。SQL 定义在 `src/main/resources/mapper/*.xml`，不使用注解 SQL。 |
| ResultMap | XML 中的 `resultMap` 必须完整映射所有查询字段到 Java 属性，使用下划线转驼峰或显式 `<result>` 映射。 |
| 模糊查询 | XML 中使用 `concat('%', #{keyword}, '%')`，不直接拼接 `%` 到 Java 代码。 |
| DTO / Command | 命令对象（Command）封装业务入参，Controller 内部类做参数校验（`@Valid` + `@NotBlank` 等）。 |
| 校验 | Controller Request DTO 使用 `jakarta.validation` 注解校验；`@Valid` 触发校验。 |
| 密钥 | `AccountApplication.secret` 是敏感字段，日志和 API 响应中不得原样暴露。 |
| 敏感对象 | 密码、Secret、Token、Session 等敏感字段不得进入 Lombok 生成的 `toString`、`equals` 或日志；优先不生成，确需生成时显式排除。 |
| 注释 | 简单代码不写废话注释；复杂业务规则、跨表组装、安全相关逻辑写简短功能注释。 |
| UUID | 用户 ID 使用 `UUID.randomUUID().toString()`，不加 `-` 分隔符。 |

## Service 与 Mapper 模式

数据访问统一由 MyBatis Mapper 完成，Service 直接注入需要的 Mapper，不再保留 Store / Repository 中间层：

```java
@Service
@RequiredArgsConstructor
public class UserManagementService {
    private final AccountUserMapper userMapper;

    public AccountUser getUser(String userId) {
        AccountUser user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("USER_NOT_FOUND", "error.userNotFound");
        return user;
    }
}
```

- 业务规则、实体构建、乐观锁判断和异常翻译写在 Service；Mapper 只声明 SQL 语句。
- 其它域需要用户或应用数据时注入对应 Service，不要绕过业务规则直接查对方的表。
- 不新增 Store / Repository 抽象层，也不新增内存或模拟实现；持久化行为统一用 H2 验证真实 Mapper。

## Mapper XML 模式

```xml
<resultMap id="userResultMap" type="com.hypers.account.app.model.AccountUser">
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

- **Command** (`SaveUserCommand`, `RegisterApplicationCommand`): 业务层入参封装，放在 `app/command` 包，不含校验注解。
- **Request DTO** (Controller 内部类): 网络层入参校验，放在 `web` 包的 Controller 内部，使用 `jakarta.validation` 注解。
- Controller 将 Request DTO 转换为 Command 后传给 Service。

```java
// Controller 内部类 - 负责校验
public static class SaveUserRequest {
    @NotBlank private String account;
    @NotBlank private String email;
    @NotBlank private String name;
    @NotBlank private String phone;
}

// Command - 业务层入参
@lombok.Value
public class SaveUserCommand {
    String account;
    String email;
    String name;
    String phone;
}
```

Request DTO 需要 JavaBean setter 供 Jackson 绑定时，使用 `@Getter` + `@Setter`。包含密码、Secret、Token 或 Session 信息的类型禁止使用会生成 `toString` 的 `@Data`；Lombok 只负责样板代码，不替代边界校验和敏感字段审查。Lombok 版本跟随项目依赖管理/BOM，作为编译期依赖启用注解处理，不在业务模块重复硬编码版本。

## 全局异常处理

业务错误统一抛 `BusinessException(ErrorCode)`，HTTP 状态、对外错误码和 i18n 消息键都由 `ErrorCode` 枚举定义：

| 位置 | 说明 |
| --- | --- |
| `ErrorCode` | 每个常量 = 状态 + 对外错误码 + `error.*` 消息键；新增错误先在枚举登记 |
| `BusinessException` | 只携带 `ErrorCode` 和可选字段级消息键，不写展示文案 |
| `AccountExceptionHandler` / `ManagementExceptionHandler` | 按请求语言解析消息键；管理端额外返回 `code`、`traceId`、`fieldErrors` |

字段校验错误使用 `new BusinessException(ErrorCode.VALIDATION_FAILED, Map.of(field, "validation.xxx"))`。
不要新增异常子类，也不要在业务代码里抛带英文文案的 `IllegalArgumentException`。

## 工具类优先级

| 场景 | 优先使用 |
| --- | --- |
| UUID 生成 | `java.util.UUID.randomUUID().toString()` |
| 时间 | `java.time.*`（项目使用 Clock 注入，便于测试） |
| 集合判空 | `java.util.Collections.isEmpty()` |
| 字符串判空 | `java.util.Optional` + `filter` |

不要在业务类中重复写私有工具方法。

## 测试

- 测试连接 H2 内存数据库，由 Flyway 建表，直接验证 Mapper 和真实 SQL；不提供内存 Store 替身。
- Spring Boot Test 使用 `@SpringBootTest` + `@AutoConfigureMockMvc`。
- JUnit 5 是默认测试框架（`spring-boot-starter-test` 自带）。
- Controller 测试验证 HTTP 状态码、响应体结构和错误处理。
- Mapper 语句由 Service 或接口集成测试覆盖，验证真实 SQL 结果。
- Service 测试验证业务编排逻辑，写库场景使用 `@SpringBootTest` + `@Transactional` 回滚。

## 验证清单

完成 Java 改动前检查：

- [ ] 包归属正确，没有跨包违规调用。
- [ ] Controller、Service、Mapper 分层正确。
- [ ] 构造器注入，无 `@Autowired` / `@Resource`。
- [ ] 当前新增/修改的 Java 样板代码使用 Lombok，敏感字段未进入生成的 `toString` / `equals`。
- [ ] 普通 URL、OpenAPI 文件名、配置键、Java 包、类和测试名不携带 API 版本号；版本由 Git/制品/发布迭代，确需并行兼容时有明确迁移与退出证据。
- [ ] 没有重新引入 Store / Repository 中间层或内存实现。
- [ ] Mapper XML `resultMap` 完整映射所有字段。
- [ ] 涉及 DDL 时，已按数据库 Skill 核对类型、默认值、空值、主外键/唯一约束及迁移兼容；逐表逐字段注释检查通过并记录命令、检查数量和遗漏项。历史缺失单列记录，不改已执行迁移。
- [ ] List 返回空集合，无 null。
- [ ] 模糊查询使用 `concat`，无 `%` 拼接到 Java 代码。
- [ ] 密钥等敏感信息未暴露在日志或响应中。
- [ ] 复杂逻辑有必要的功能注释。
- [ ] 执行受影响模块构建；若需跑测试，使用 `mvn test`。
