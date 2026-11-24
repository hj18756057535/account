# Account 标准 OIDC 运行与接入

更新日期：2026-11-24。协议端点使用 Spring Authorization Server 1.5.8，由现有 Boot 3.5.16 管理版本。应用和当前 Secret 改由管理页面维护，替代逐客户端 Secret 摘要配置。
本轮实现授权码 + PKCE S256、专用客户端认证、Discovery、JWKS、UserInfo 和 RP-Initiated Logout；功能/工具 RBAC、来源系统会话桥接和多实例共享会话仍是后续范围。

## 启用方式

CMS 当前页面接入流程见 [部署对接方案](../../docs/application-integration/CMS接入-部署对接方案.md)。`Initialize-Oidc.ps1` 可以准备或复用持久化签名材料；历史生成的客户端 Secret/摘要不再替代应用页面当前 Secret。下面的环境变量方式用于全局签名与 issuer 配置。

默认不开启。手工部署时先配置测试数据库，登记应用并授权测试用户，再设置下列 OIDC 环境变量，以 oidc profile 启动；准备脚本生成的配置可直接加载。
配置入口：[application-oidc.yml](../account-center-server/src/main/resources/application-oidc.yml)。

| 环境变量 | 含义 |
| --- | --- |
| ACCOUNT_OIDC_ISSUER | 固定外部发行者地址，如 https://account.example.com，不随请求 Host 自动变化 |
| ACCOUNT_OIDC_ALLOW_INTRANET_HTTP | 默认 false；受控内网明确选择私有 IP HTTP 时可设 true，准备工具按地址生成 |
| ACCOUNT_OIDC_KEY_STORE | 持久化 PKCS12 Resource，如 file:D:/account-secrets/oidc-signing.p12 |
| ACCOUNT_OIDC_KEY_STORE_PASSWORD | PKCS12 密码，安全配置 |
| ACCOUNT_OIDC_SIGNING_KEY_ALIAS | 当前 RSA 签名私钥别名，同时作为 kid |
| ACCOUNT_OIDC_AGENT_POST_LOGOUT_REDIRECT_URI | 精确登记的 Agent 退出回调 |
| ACCOUNT_SESSION_COOKIE_SECURE | HTTPS 使用 true；本机或已选择的内网 HTTP 使用 false，准备工具自动生成 |

示例 profile 中的 agent 条目仅演示可选的退出回调；其他应用不用新增客户端配置条目。
client_id 必须是已有 Account 应用编码。登录回调直接使用该应用登记的 ssoCallbackUrl，不另存一份重复回调。
退出回调来自客户端配置。HTTPS 可以使用固定 IP；本机 HTTP 默认允许，私有 IP HTTP 需显式打开内网兼容开关，公网 HTTP 仍拒绝。内网 HTTP 放宽了规范的 HTTPS 传输要求，客户端需接受该环境的兼容设置。

用现有 JDK 21 Maven 包装器构建，再按项目运行方式启用 profile：
`java -jar account-center-server/target/account-center-server-0.1.0-SNAPSHOT.jar --spring.profiles.active=oidc`
执行前必须显式设置 ACCOUNT_DB_URL、ACCOUNT_DB_USERNAME、ACCOUNT_DB_PASSWORD，选择批准的测试库。
此命令仅为运维说明，本轮没有实际启动并连接任何外部数据库。

## 密钥与客户端

使用 JDK keytool 在安全目录创建至少 2048 位 RSA 的 PKCS12，密码从环境读取。示例仅演示参数，管理员在目标环境执行：

`keytool -genkeypair -alias account-signing -keyalg RSA -keysize 3072 -dname "CN=Account Center" -validity 365 -storetype PKCS12 -keystore <安全目录>/oidc-signing.p12 -storepass:env ACCOUNT_OIDC_KEY_STORE_PASSWORD`

文件必须持久化并限制访问；不同环境使用不同文件。应用启动不生成私钥，配置缺失或文件无效时启动失败。
轮换时在同一 PKCS12 中保留尚有有效 Token 的旧 RSA 证书，为新私钥分配新的别名/kid，并切换 signing-key-alias。
JWKS 只公开公钥；当前别名用于签名，其他别名只提供历史公钥。先发布新公钥再切换签名，旧公钥保留到相关 Token 全部失效。
RSA 私钥、PKCS12 密码不得提交到仓库或经管理接口返回。

应用管理页创建或轮换时一次显示的 Secret 即客户端 `client_secret`，也用于该应用的 HMAC 服务接入。Account 在客户端认证时使用当前值生成 BCrypt 认证值；`clients.<app>.secret-hash` 不再参与认证，无需人工生成或复制摘要。
轮换或撤销实时影响后续客户端认证；更新客户端保存的 Secret 并重启客户端后再连接。签名文件和签名密码仍是 Account 独立的全局配置，不能使用应用 Secret 替代。

Account 客户端适配器实时读取数据库中的应用状态、名称、回调和可用 Secret；填写 SSO 回调即可提供登录，不另设能力开关。可选退出回调仍来自外部配置。
不开放动态客户端注册。client_secret_basic + authorization_code + openid/profile 为当前支持的客户端组合，强制 S256 PKCE，不提供 refresh_token 或 client_credentials。

## 应用准入规则

在现有应用管理中登记 Agent、回调和用户准入：
- 只接标准登录的应用填写 SSO 回调，后端服务地址留空；不生成用户同步命令。
- 填写后端服务地址的应用使用“准入期望状态 + 当前版本同步回执确认”条件。
- OIDC 在授权、兑换和 UserInfo 时检查用户/应用启用状态及当前准入，撤权后后续请求拒绝。

纯 OIDC 应用以 Account 的有效准入期望状态为依据，不制造 localUserId 或虚假的 Provider 回执。授权和撤权无需理由，审计自动记录。
当前不提供 Agent 功能/工具授权查询接口，OIDC 登录成功不意味着工具可执行；该 RBAC 切片尚待实现。

## 持久化与会话

当前系统尚未使用，OIDC 表已包含在两种方言的最新 V1 初始化中，不保留未发布版本的迁移或历史兼容。开发者提供空库初始化，应用不自动清库。
account_oidc_authorizations 保存官方协议对象所需的内部载荷和 token 查找摘要；载荷包含敏感凭据，仅服务端访问，不进入日志或管理响应。
不要启用该 Mapper 的明文 SQL 参数日志。关闭 OIDC 时保留授权表和数据；Agent 后续登录按标准错误处理，不相信未经验证的来源用户资料。
Store/MyBatis 实现官方 OAuth2AuthorizationService SPI，官方引擎负责协议校验、Token 生成和重放处理，避免复制协议 Controller。
用原子条件更新确保并发兑换只有一个结果能保存并返回；重放仍按官方逻辑使已发出的授权失效。

登录继续复用 Account 用户与 BCrypt 密码校验；启用 OIDC 时由 Spring Security 处理登录 POST、CSRF 与 Session ID 更新。
现有可信 AccountSessionUser 会话每次转换为稳定全局用户的标准身份；管理登录和旧票据入口可继续工作。
默认 Servlet 会话为单实例。授权记录和密钥可跨服务对象恢复，不代表多实例登录会话已经共享；部署多实例前需单独接入共享会话并验证完整路径。

授权码默认 60 秒（允许 30～120 秒），access_token 默认 5 分钟（允许 1～15 分钟）。
过期授权记录不会自动删除，需按运维保留策略清理；首版无刷新令牌，按更新时间超过一天的记录可在核对时间和保留策略后处理，本轮不执行清理 SQL。

## 协议入口与验证

启用后从固定 issuer 的 /.well-known/openid-configuration 获取端点，不让客户端猜测：
- /oauth2/authorize
- /oauth2/token
- /oauth2/jwks
- /userinfo
- /connect/logout
- 官方 /oauth2/introspect、/oauth2/revoke 也受客户端认证保护；撤权后的用户访问令牌内省为 inactive。

以 [Agent 平台对接说明](../../docs/application-integration/Agent平台-SSO对接说明.md) 为客户端交接入口。
开启标准 SSO 不会自动接受 CMS 的本地登录态；旧来源系统桥接、下游 CMS 用户凭据和功能/工具权限需各自完成。

验证结果维护在 [当前计划](../../docs/account-center/账号中心-计划.md)。自动集成测试使用隔离 H2 和临时生成的测试密钥，不等于真实 PG/MySQL、网关、Agent 客户端、浏览器或部署已验收。
