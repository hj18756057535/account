# Account Center Spring Boot 2 Starter

适用于 Java 8/11、Spring Boot 2 的 Servlet 应用；自动测试基线是现有消费者使用的 Boot 2.6.4。此版本提供 `menu_permission_v1` 菜单权限 Provider，不包含 Boot 3 Starter 的 SSO/用户同步能力。Boot 2.6.4 是兼容基线，不是新项目推荐版本。

## 安装与引用

在 `D:\projects\account-center` 执行（使用 JDK 21 构建、`--release 8` 生成 Java 8 字节码）：

```powershell
.\scripts\Invoke-MavenJdk21.ps1 -f account/pom.xml -pl account-center-spring-boot2-starter -am install
```

业务项目使用相同 Maven 本地仓库，并添加：

```xml
<dependency>
    <groupId>com.hypers</groupId>
    <artifactId>account-center-spring-boot2-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Starter JAR 内含菜单协议、Handler 接口和签名校验实现。它不依赖 Java 21 Contract，也不继承 Account 的 Boot 3 父 POM。宿主应统一管理其 Boot 2 依赖版本。其他电脑/CI 需各自安装同版本制品，或配置共享 Maven 制品仓库；本机安装不会自动供远程机器使用。后续更新先重新 install，再重新构建并启动消费者。

## 启用

```yaml
account:
  integration:
    menu-permission:
      enabled: true
      app-code: ${ACCOUNT_MENU_PERMISSION_APP_CODE}
      secret: ${ACCOUNT_MENU_PERMISSION_SECRET}
      allowed-clock-skew-millis: 300000
```

业务应用必须提供 `AccountMenuPermissionHandler` 和 `AccountNonceStore` Bean。Handler 实现查询与整集替换，在业务事务内处理 revision 与至少 24 小时幂等结果；生产 NonceStore 使用共享 Redis 等原子去重存储。没有内存自动降级。缺少必需 Bean/配置时启动失败；默认关闭时不注册端点。

Starter 注册现有 Controller、Advice 和签名 Filter，Filter 仅保护 `/account-integration/v1/menu-permissions/query` 与 `/account-integration/v1/menu-permissions`，顺序为 `Ordered.HIGHEST_PRECEDENCE + 10`。宿主仍负责网关可达范围与 JWT 路径处理，核对自定义 Filter 顺序，不能绕过 HMAC。不要扫描 `com.hypers.account`，由 Boot 自动配置注册组件。

## 从复制源码迁移

1. 安装制品，给业务模块添加上述依赖。
2. 对照差异，移除业务项目中复制的 `com.hypers.account.integration.menu` 公共类；不能同时保留同名源码与依赖 JAR。
3. 保留业务 Handler、Redis NonceStore、授权事务、幂等存储和缓存失效逻辑。
4. 移除原配置中手工注册的公共 Controller、Advice、签名 Filter，保留业务 Bean 注册，配置上述属性；不要开启 Bean 覆盖掩盖重复注册。
5. 验证合法签名查询/保存、错误签名、重放拒绝、权限冲突、重复提交及登录缓存失效。

cms-ai 已于 2026-09-17 迁移：移除复制公共类和 `CmsAccountMenuPermissionConfiguration`，保留业务实现。配置前缀原本就与 Starter 相同；变化是必须显式启用。初次迁移执行 clean 清除旧 class，详细步骤和登录衔接见 [老应用接入指南](../docs/legacy-application-onboarding.md)。
