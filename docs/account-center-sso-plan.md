# Account Center 一体化 SSO 与应用授权实施计划

## Summary

新建一个前后端不分离的 Account Center。它负责用户管理、应用管理、应用准入授权、用户同步、SSO 登录入口和审计；各应用继续保留自己的账号、角色、菜单和数据权限体系。Account 只统一“用户能否进入应用”，应用内细权限通过 iframe 嵌入各应用自己的授权页面完成。

## Architecture

- 单体 Spring Boot 服务，一个 jar 部署；后端 API 和管理页面由同一个服务承载。
- 页面使用 Thymeleaf 模板由 Spring Boot 直接渲染，部署保持前后端不分离。
- 后端使用 Spring Boot + Spring JDBC；默认使用 PostgreSQL 存储数据，同时提供 MySQL profile。
- SSO 使用“一次性 code 兑换”：Account 生成短期 code，应用后端兑换用户资料后签发自己的本地 token。

## Core Features

- 用户管理：新增、编辑、启用、禁用用户；用户先只保存在 Account 中。
- 应用管理：维护应用编码、名称、入口地址、SSO 回调地址、iframe 授权页地址、接口通知地址、密钥、默认租户。
- 应用准入授权：给用户授权某应用时，Account 调用应用接口创建或更新本地用户；取消授权时调用应用禁用或解绑接口。
- 应用内权限配置：Account 页面通过 iframe 打开应用自己的授权页，传递一次性管理票据；角色、菜单、按钮、数据权限由应用自己保存。
- 默认租户：没有租户概念的应用统一使用 `default` 租户，应用侧可忽略其业务含义。

## Current Implementation Notes

- 数据表：`account_users`、`account_applications`、`account_user_applications`。
- 通用建表脚本：`src/main/resources/db/schema.sql`。
- PostgreSQL 默认连接配置：`ACCOUNT_DB_URL`、`ACCOUNT_DB_USERNAME`、`ACCOUNT_DB_PASSWORD`。
- MySQL 启动方式：启用 `mysql` profile，并使用同一组环境变量覆盖连接信息。
- 页面拆分：
  - `/`：入口导航。
  - `/users`：用户创建、应用准入授权、SSO Code 生成、iframe 授权页调试。
  - `/applications`：应用接入信息维护。

## Application Integration

应用必须实现接口：

- `POST /account-sso/internal/users/upsert`
- `POST /account-sso/internal/users/disable`
- `GET /account-sso/callback?code=&state=`
- `GET /account-sso/internal/admin/authorize-page-ticket/verify` 或等价校验接口

应用可选提供 iframe 页面：

- `/account-admin/users/{externalUserId}/permissions?ticket=...`

Account 通过 HMAC 签名调用应用接口，字段包含 `appCode`、`timestamp`、`nonce`、`signature`。

Starter 包提供 DTO、签名校验、code 兑换客户端、默认 Controller 骨架和应用侧扩展接口。

## cms-ai 接入

- `cms-ai` 引入 Starter，放行 `/account-sso/**`。
- 已存在用户通过 `SysUserService.getUserByCount(account)` 定位。
- SSO 登录成功后调用 `AuthService.doLogin(SysUser)` 签发 Snowy 本地 JWT。
- 首次授权时自动创建本地用户，并补齐默认组织、默认角色、默认租户 `default`。
- iframe 授权页复用 `cms-ai` 自己的角色/菜单体系，不把 Snowy 菜单模型搬进 Account。

## Test Plan

- Account：用户新增不自动同步；授权应用触发 upsert；取消授权触发 disable；编辑用户只同步已授权应用。
- SSO：未授权用户不能生成 code；code 过期、重复兑换、签名错误、nonce 重放均失败。
- iframe：管理票据过期、应用不匹配、用户未授权时拒绝打开授权页。
- `cms-ai`：已有账号可登录；首次授权自动建号；默认组织/角色缺失时返回明确错误；应用内角色菜单仍由 `cms-ai` 自己控制。

## Assumptions

- 第一版只保留接口通知，不再实现 MQ。
- Account 不中心化管理各应用角色和菜单。
- 前后端不分离，一个 Spring Boot 服务承载 API 和页面。
- 无租户应用统一使用 `default`。
