# Account 菜单权限 Java 8 源码接入包

本目录是 `menu_permission_v1` 的可复制源码，不是需要发布到 Nexus 的运行时依赖。适用于 Java 8、`javax.servlet`、Spring MVC 5（包括 Spring Boot 2.x）应用，并要求宿主项目启用 Lombok；线上事实源仍是 `../../openapi/account-application-api.yaml` 与 `../conformance/menu_permission_v1.json`。

## 接入步骤

1. 复制 `src/main/java/com/hypers/account/integration/menu` 到业务应用源码目录，保持包名与 `SOURCE-KIT-MANIFEST.json`。
2. 实现 `AccountMenuPermissionHandler`。查询必须返回真实目录和授权；保存必须在业务事务内校验 revision、整集替换托管权限，并持久化至少 24 小时的幂等键、请求摘要与首次成功结果。
3. 注册 `AccountMenuPermissionController`、`AccountIntegrationExceptionHandler` 和 `AccountMenuPermissionSignatureFilter`。Filter 只映射两个固定 Provider 路径，顺序必须早于应用 JWT Filter。
4. 生产环境实现 `AccountNonceStore`，使用共享 Redis 等多实例存储；`InMemoryAccountNonceStore` 只用于单实例开发和测试。
5. 从环境变量或配置中心注入 appCode、Secret 与 5 分钟时间窗。不得把 Secret 提交到配置、日志、示例或 manifest。
6. 网关只允许 Account Server 访问 Provider 路径；浏览器不直接调用应用。

最小 Spring 配置需要显式构造 Controller、Advice 和 Filter；源码包不提供 Boot 自动装配，避免绑定宿主应用版本。升级源码包时先比较 manifest 和一致性向量，协议类不得在业务项目中自行修改。

## 本地验证

在本目录使用 JDK 21 也会按 Java 8 target 编译：

```powershell
mvn test
```

本测试工程中的 Spring/Jackson 版本只用于证明 Java 8/`javax` 兼容性；复制到业务应用后使用宿主已经管理的兼容版本，不需要把这些版本再次写进业务 POM。
