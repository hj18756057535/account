package com.hypers.account.oidc;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("account.oidc")
public class OidcProperties {
    /** 是否启用标准 OIDC，默认关闭；启用时必须配置 issuer、密钥和客户端。 */
    private boolean enabled;
    /** 固定发行者地址，无默认值；可使用固定 IP，默认仅允许 HTTPS 或本机 HTTP。 */
    private String issuer;
    /** 内网 HTTP 兼容开关，默认关闭；只放行私有 IP，不放行公网 HTTP 或任意域名。 */
    private boolean allowIntranetHttp;
    /** 持久化 PKCS12 的 Spring Resource 地址，无默认值，不在启动时生成密钥。 */
    private String keyStore;
    /** PKCS12 密码，无默认值，通过 Secret 提供，不参与日志。 */
    private String keyStorePassword;
    /** 当前签名私钥的别名，同时作为 kid；其他证书仅供历史验签。 */
    private String signingKeyAlias;
    /** 授权码有效期，默认 60 秒，允许 30～120 秒。 */
    private Duration codeTtl = Duration.ofSeconds(60);
    /** 访问令牌有效期，默认 5 分钟，允许 1～15 分钟。 */
    private Duration accessTokenTtl = Duration.ofMinutes(5);
    /** 以现有 appCode 为键的客户端；应用和登录回调由 Account 数据库管理。 */
    private Map<String, Client> clients = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Client {
        /** 专用 BCrypt 客户端密钥摘要，无默认值，不复用应用 HMAC Secret。 */
        private String secretHash;
        /** 精确登记的退出回调，默认空列表，不能由请求方任意指定。 */
        private List<String> postLogoutRedirectUris = List.of();
    }
}
