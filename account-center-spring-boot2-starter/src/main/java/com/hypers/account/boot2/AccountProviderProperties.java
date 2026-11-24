package com.hypers.account.boot2;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 应用目录和用户同步 Provider 的共享接入参数。 */
@Getter
@Setter
@ConfigurationProperties("account.integration.provider")
public class AccountProviderProperties {
    /** 是否提供目录和用户同步接口，默认关闭。 */
    private boolean enabled;
    /** 页面登记的应用编码，启用时必填。 */
    private String appCode;
    /** 页面生成的当前应用 Secret，启用时必填。 */
    private String secret;
    /** 签名时间窗，单位毫秒，默认 300000，允许 1 至 300000。 */
    private long allowedClockSkewMillis = 300000L;
}
