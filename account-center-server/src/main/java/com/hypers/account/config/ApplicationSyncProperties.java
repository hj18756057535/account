package com.hypers.account.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.EnableScheduling;

@Component
@EnableScheduling
@ConfigurationProperties("account.application-sync")
@Getter
@Setter
public class ApplicationSyncProperties {
    /** 全局投递和目录调用开关，默认开启；具体服务地址由应用管理页面登记。 */
    private boolean enabled = true;

}
