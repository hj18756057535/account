---
name: account-query-performance
description: 在 Account Center 仓库中诊断或优化 MyBatis 慢查询、N+1、循环查库、批量查询和集合映射时使用。
---

# Account Center 查询性能规则

本 Skill 用于查询性能优化，尤其是 MyBatis Mapper 中的 N+1、循环查库和批量映射问题。

## 根因定位

先找这些信号：

- `for` / `forEach` / stream 中调用 Mapper 的 `selectById`、`selectByAppCode` 等单条查询。
- 授权检查时对每个用户/应用逐个查询数据库。
- 多个用户 ID、多个 appCode 逐个查询。
- 两个 List 嵌套循环匹配，复杂度 O(n²)。
- XML 中 `LIKE` 直接使用用户输入且未用 `concat` 转义。
- 查出用户列表后，对每个用户再查授权应用。

## 优化流程

1. 明确主数据集合和需要补充的关联数据。
2. 提取 ID / appCode 集合，过滤空值并去重。
3. 在 Mapper 中新增批量查询方法（接受 `List` 参数）。
4. 在 XML 中使用 `<foreach>` 标签构建 `IN` 查询。
5. 使用 `Map`、`groupingBy` 做内存映射。
6. 只组装当前接口需要的字段。

## 推荐模式

### Mapper 接口新增批量方法

```java
List<AccountUser> selectByIds(@Param("ids") List<String> ids);

List<AccountApplication> selectByAppCodes(@Param("appCodes") List<String> appCodes);
```

### Mapper XML 使用 foreach

```xml
<select id="selectByIds" resultMap="userResultMap">
    select id, account, email, name, phone, status, created_by, updated_by, created_at, updated_at
    from account_users
    where id in
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</select>
```

### Java 层批量映射

```java
// 提取 ID 集合
List<String> userIds = userApplications.stream()
        .map(AccountUserApplication::getUserId)
        .filter(id -> id != null && !id.isEmpty())
        .distinct()
        .collect(Collectors.toList());

// 一次性批量查询
Map<String, AccountUser> userMap = store.findUsersByIds(userIds).stream()
        .collect(Collectors.toMap(AccountUser::getId, u -> u));

// 内存映射组装
for (AccountApplication app : applications) {
    AccountUser user = userMap.get(app.getCreatedBy());
    // ...
}
```

### Store 层封装批量方法

```java
// AccountStore 接口
List<AccountUser> findUsersByIds(List<String> ids);
Map<String, List<AccountApplication>> findAuthorizedApplicationsByUserIds(List<String> userIds);
```

批量查询方法应同时在 `MyBatisAccountStore` 和 `InMemoryAccountStore` 中实现。

## LIKE 查询

XML 中模糊查询必须使用 `concat`：

```xml
-- 正确
and account like concat('%', #{keyword}, '%')

-- 错误：直接拼接，SQL 注入风险
and account like '%${keyword}%'
```

Java 代码中不要手动拼接 `%` 到参数中再传给 Mapper。

## 空集合保护

批量查询方法必须对空输入做保护：

```java
// MyBatisAccountStore
@Override
public List<AccountUser> findUsersByIds(List<String> ids) {
    if (ids == null || ids.isEmpty()) {
        return Collections.emptyList();
    }
    return userMapper.selectByIds(ids);
}
```

XML 中 `<foreach>` 对空集合会生成 `WHERE id IN ()`，这在 PostgreSQL 中是语法错误。

## 跨表关联

当前项目的典型跨表关联：

| 场景 | 关联方式 |
| --- | --- |
| 用户 -> 已授权应用 | `account_user_applications` JOIN `account_applications` |
| 应用 -> 已授权用户 | `account_user_applications` JOIN `account_users` |
| 操作日志 -> 操作人 | `account_operation_logs` LEFT JOIN `account_users` |

- 两个表的简单关联可以在 XML 中使用 `JOIN` 查询。
- 已有模式参考 `AccountUserApplicationMapper.xml` 的 `selectAuthorizedApplications`。
- 如果关联逻辑可复用，在 Mapper XML 中封装语义化查询方法。

## 验证

完成前确认：

- [ ] 没有循环查库（`for` 中调 Mapper）。
- [ ] 没有 O(n²) 列表匹配。
- [ ] 批量查询对空集合直接返回空集合。
- [ ] `toMap` 处理重复 key，或业务确认不会重复。
- [ ] `InMemoryAccountStore` 和 `MyBatisAccountStore` 中新增的批量方法行为一致。
- [ ] XML 中 `<foreach>` 语法正确，参数类型匹配。
- [ ] 受影响模块编译或测试已执行；若无法执行，说明原因。

