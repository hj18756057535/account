package com.hypers.account.starter.properties;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("account.integration")
public class AccountIntegrationProperties {

    @NotBlank
    private String accountBaseUrl;

    @NotBlank
    private String appCode;

    @NotBlank
    private String secret;

    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private Duration allowedClockSkew = Duration.ofMinutes(5);
    private boolean providerEnabled = true;
}
