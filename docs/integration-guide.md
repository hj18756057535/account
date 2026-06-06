# Account Center 应用对接指南

本指南帮助业务应用接入 Account Center，实现 SSO 单点登录和用户同步。

## 1. 对接概览

业务应用接入 Account Center 后获得以下能力：

| 能力 | 说明 |
|------|------|
| SSO 单点登录 | 用户从应用跳转 Account Center 登录，登录后自动回到应用 |
| 用户同步 | Account Center 授权/取消授权用户时，自动通知应用创建/禁用本地用户 |
| iframe 权限配置 | Account Center 管理页面通过 iframe 打开应用的权限配置页 |

**核心原则：** Account Center 只做准入控制（哪些用户能进哪些应用），应用继续维护自己的角色、菜单、按钮和数据权限。

## 2. 引入 Starter

### 2.1 Maven 依赖

```xml
<dependency>
    <groupId>com.hypers</groupId>
    <artifactId>account-center-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2.2 Starter 提供的内容

| 类型 | 类名 | 说明 |
|------|------|------|
| 客户端 | `AccountSsoClient` | SSO code 兑换 |
| 客户端 | `AccountAdminTicketClient` | 管理 ticket 校验 |
| 签名 | `AccountHmacSigner` | HMAC-SHA256 签名/验签 |
| DTO | `AccountUserUpsertRequest` | 用户同步请求 |
| DTO | `SsoTicketExchangeRequest/Response` | SSO 兑换请求/响应 |
| DTO | `AdminTicketVerifyRequest/Response` | ticket 校验请求/响应 |
| 接口 | `AccountUserProvisionService` | 用户同步（应用实现） |
| 接口 | `AccountSsoLoginHandler` | SSO 登录（应用实现） |

## 3. 在 Account Center 注册应用

通过 Account Center 管理页面或 API 注册应用：

```bash
curl -X POST http://localhost:8088/api/applications \
  -H "Content-Type: application/json" \
  -d '{
    "appCode": "your-app",
    "name": "你的应用",
    "entryUrl": "http://localhost:9003",
    "ssoCallbackUrl": "http://localhost:9003/account-sso/callback",
    "permissionIframeUrl": "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
    "notifyBaseUrl": "http://localhost:9003",
    "secret": "your-hmac-secret",
    "defaultTenantCode": "default"
  }'
```

**字段说明：**

| 字段 | 说明 |
|------|------|
| `appCode` | 应用唯一编码 |
| `entryUrl` | 应用入口地址 |
| `ssoCallbackUrl` | SSO 登录回调地址（含 `/account-sso/callback`） |
| `permissionIframeUrl` | iframe 授权页面地址（`{externalUserId}` 会被替换为实际用户 ID） |
| `notifyBaseUrl` | 用户同步通知基地址 |
| `secret` | HMAC 签名密钥（妥善保管，不要提交到代码仓库） |
| `defaultTenantCode` | 默认租户编码（无租户场景用 `default`） |

注册后记录返回的 `secret`（密钥只在创建和轮换时返回）。

## 4. 实现用户同步

### 4.1 实现 `AccountUserProvisionService`

```java
@Service
public class YourAppUserProvisionService implements AccountUserProvisionService {

    private final YourUserRepository userRepository;

    public YourAppUserProvisionService(YourUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void upsertUser(AccountUserUpsertRequest request) {
        // 按 account 查找本地用户
        YourUser localUser = userRepository.findByAccount(request.getAccount());
        if (localUser != null) {
            // 更新已有用户
            localUser.setEmail(request.getEmail());
            localUser.setName(request.getName());
            localUser.setPhone(request.getPhone());
            localUser.setStatus("enabled");
            userRepository.save(localUser);
        } else {
            // 创建新用户，分配默认角色
            YourUser newUser = new YourUser();
            newUser.setAccount(request.getAccount());
            newUser.setEmail(request.getEmail());
            newUser.setName(request.getName());
            newUser.setPhone(request.getPhone());
            newUser.setExternalUserId(request.getExternalUserId());
            newUser.setStatus("enabled");
            newUser.setTenantCode(request.getTenantCode());
            // 分配默认组织和角色（按你的业务逻辑）
            userRepository.save(newUser);
        }
    }

    @Override
    public void disableUser(AccountUserUpsertRequest request) {
        YourUser localUser = userRepository.findByAccount(request.getAccount());
        if (localUser != null) {
            localUser.setStatus("disabled");
            userRepository.save(localUser);
        }
    }
}
```

### 4.2 注册用户同步 Controller

Starter 提供了 `AccountInternalUserController` 默认实现。如果你的框架需要自定义，创建以下 Controller：

```java
@RestController
public class AccountSyncController {

    private final AccountUserProvisionService provisionService;

    public AccountSyncController(AccountUserProvisionService provisionService) {
        this.provisionService = provisionService;
    }

    @PostMapping("/account-sso/internal/users/upsert")
    public String upsert(@RequestBody AccountUserUpsertRequest request) {
        if (request.isEnabled()) {
            provisionService.upsertUser(request);
        } else {
            provisionService.disableUser(request);
        }
        return "ok";
    }
}
```

### 4.3 放行同步路径

在你的安全配置中放行以下路径：

```
/account-sso/internal/users/**
```

## 5. 实现 SSO 登录

### 5.1 配置 SSO 客户端

```java
@Configuration
public class AccountSsoConfig {

    @Value("${account.center.url:http://localhost:8088}")
    private String accountCenterUrl;

    @Value("${account.app.code:your-app}")
    private String appCode;

    @Value("${account.app.secret:your-hmac-secret}")
    private String secret;

    @Bean
    public AccountSsoClient accountSsoClient() {
        return new AccountSsoClient(accountCenterUrl, appCode, secret);
    }

    @Bean
    public AccountAdminTicketClient accountAdminTicketClient() {
        return new AccountAdminTicketClient(accountCenterUrl, appCode, secret);
    }
}
```

### 5.2 实现 `AccountSsoLoginHandler`

```java
@Service
public class YourAppSsoLoginHandler implements AccountSsoLoginHandler {

    private final YourUserRepository userRepository;
    private final YourAuthService authService;

    public YourAppSsoLoginHandler(YourUserRepository userRepository, YourAuthService authService) {
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public String handleLogin(SsoTicketExchangeResponse user) {
        // 按 account 查找或创建本地用户
        YourUser localUser = userRepository.findByAccount(user.getAccount());
        if (localUser == null) {
            // 用户不存在，可能同步还未到达，这里兜底创建
            localUser = createLocalUser(user);
        }

        // 签发本地 token（使用你自己的认证逻辑）
        String token = authService.doLogin(localUser);

        // 返回重定向 URL（带 token 或跳转到前端）
        return "/login-success?token=" + token;
    }
}
```

### 5.3 创建 SSO 回调端点

```java
@Controller
public class SsoCallbackController {

    private final AccountSsoClient ssoClient;
    private final AccountSsoLoginHandler loginHandler;

    public SsoCallbackController(AccountSsoClient ssoClient, AccountSsoLoginHandler loginHandler) {
        this.ssoClient = ssoClient;
        this.loginHandler = loginHandler;
    }

    @GetMapping("/account-sso/callback")
    public String callback(@RequestParam String code,
                           @RequestParam(required = false) String state) {
        // 用 code 向 Account Center 兑换用户信息
        SsoTicketExchangeResponse user = ssoClient.exchange(code);

        // 调用应用侧登录处理器
        String redirectUrl = loginHandler.handleLogin(user);

        return "redirect:" + redirectUrl;
    }
}
```

### 5.4 前端跳转 SSO

在你的应用前端发现未登录时，跳转到 Account Center：

```
GET http://localhost:8088/sso/authorize
    ?appCode=your-app
    &redirectUri=http://localhost:9003/account-sso/callback
    &state=random-string
```

### 5.5 放行 SSO 路径

```
/account-sso/callback
```

## 6. 实现 iframe 授权页面（可选）

如果你的应用需要在 Account Center 管理页面中通过 iframe 配置权限：

### 6.1 创建授权页面

提供一个页面 URL，如 `/account-admin/users/{externalUserId}/permissions`，展示该用户的角色、菜单、按钮、数据权限配置。

### 6.2 校验 admin ticket

页面加载时，从 URL 参数获取 `ticket`，调用 Account Center 校验：

```java
@Controller
public class PermissionPageController {

    private final AccountAdminTicketClient ticketClient;

    public PermissionPageController(AccountAdminTicketClient ticketClient) {
        this.ticketClient = ticketClient;
    }

    @GetMapping("/account-admin/users/{externalUserId}/permissions")
    public String permissions(@PathVariable String externalUserId,
                              @RequestParam String ticket,
                              Model model) {
        // 校验 ticket
        AdminTicketVerifyResponse verifyResult = ticketClient.verify(ticket);

        // 校验 userId 匹配
        if (!externalUserId.equals(verifyResult.getUserId())) {
            return "error";
        }

        // 渲染权限配置页面
        model.addAttribute("userId", externalUserId);
        return "permission-config";
    }
}
```

### 6.3 放行 iframe 路径

```
/account-admin/users/*/permissions
```

## 7. HMAC 签名校验（建议实现）

为了安全，建议应用校验来自 Account Center 的同步请求签名：

```java
@Component
public class AccountSignatureFilter implements Filter {

    private final AccountHmacSigner signer = new AccountHmacSigner();
    private final String secret = "your-hmac-secret";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;

        String appCode = httpReq.getHeader("X-Account-App-Code");
        String timestamp = httpReq.getHeader("X-Account-Timestamp");
        String nonce = httpReq.getHeader("X-Account-Nonce");
        String signature = httpReq.getHeader("X-Account-Signature");

        if (signature == null) {
            ((HttpServletResponse) response).sendError(401, "Missing signature");
            return;
        }

        // 检查 timestamp 在 5 分钟内
        long requestTime = Long.parseLong(timestamp);
        if (Math.abs(System.currentTimeMillis() - requestTime) > 300_000) {
            ((HttpServletResponse) response).sendError(401, "Request expired");
            return;
        }

        // 读取 body 用于签名验证
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(httpReq);
        String body = new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);

        String signText = signer.buildSignText("POST", httpReq.getRequestURI(), timestamp, nonce, body);
        if (!signer.verify(signText, secret, signature)) {
            ((HttpServletResponse) response).sendError(401, "Invalid signature");
            return;
        }

        chain.doFilter(wrappedRequest, response);
    }
}
```

## 8. 完整放行路径清单

| 路径 | 用途 |
|------|------|
| `/account-sso/callback` | SSO 登录回调 |
| `/account-sso/internal/users/**` | 用户同步通知 |
| `/account-admin/users/*/permissions` | iframe 授权页面 |

## 9. 配置示例

```yaml
# 你的应用 application.yml
account:
  center:
    url: http://localhost:8088
  app:
    code: your-app
    secret: ${ACCOUNT_SECRET:your-hmac-secret}
```

## 10. 对接检查清单

- [ ] 引入 `account-center-starter` 依赖
- [ ] 在 Account Center 注册应用，获取 appCode 和 secret
- [ ] 实现 `AccountUserProvisionService`（upsertUser + disableUser）
- [ ] 实现 `AccountSsoLoginHandler`（handleLogin → 签发本地 token）
- [ ] 创建 SSO 回调端点 `/account-sso/callback`
- [ ] 前端未登录时跳转 `/sso/authorize`
- [ ] 放行 `/account-sso/**` 路径
- [ ] （可选）实现 HMAC 签名校验过滤器
- [ ] （可选）实现 iframe 授权页面 + admin ticket 校验
- [ ] 端到端测试：从应用跳转 → Account Center 登录 → 回到应用

## 11. cms-ai 接入示例

以双碳服务（cms-ai）为例，基于 Snowy-Cloud 框架：

**模块位置：** `snowy-biz/snowy-system` 或 `custom-base`

**关键改动：**

1. 在 `snowy-system` 的 `pom.xml` 引入 starter
2. 创建 `AccountUserProvisionServiceImpl`，调用 `SysUserService` 的用户 CRUD
3. 创建 `AccountSsoLoginHandlerImpl`，调用 `AuthServiceImpl.doLogin(sysUser)` 签发 JWT
4. 在 `SpringSecurityConfig` 白名单添加 `/account-sso/**`
5. 在网关 `AccessFilter` 的白名单添加 `/account-sso/**`
6. SSO 回调后通过 `AuthServiceImpl.doLogin()` 获取本地 JWT，重定向到前端
