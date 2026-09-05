package com.hypers.account.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
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
    private boolean enabled = true;
    private Map<String, URI> targets = new HashMap<>();
}
