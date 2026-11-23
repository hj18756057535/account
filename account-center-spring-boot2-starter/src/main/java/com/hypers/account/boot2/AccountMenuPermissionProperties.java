package com.hypers.account.boot2;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "account.integration.menu-permission")
public class AccountMenuPermissionProperties {
    private boolean enabled;
    private String appCode;
    private String secret;
    private long allowedClockSkewMillis = 300000L;
}
