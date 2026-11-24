package com.hypers.account.boot2.oidc;

import java.net.URI;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("account.integration.oidc")
public class AccountOidcProperties {

    /** Account 发布的固定 issuer。启用时必填。 */
    private URI issuer;

    /** 在 Account 登记的客户端和应用编码。启用时必填。 */
    private String clientId;

    /** OIDC 客户端密钥，由宿主运行环境提供。启用时必填。 */
    private String clientSecret;

    /** 浏览器访问的完整回调地址，含部署前缀。启用时必填。 */
    private URI redirectUri;

    /** 宿主前端的登录页地址，与回调地址同源。启用时必填。 */
    private URI loginPage;
}
