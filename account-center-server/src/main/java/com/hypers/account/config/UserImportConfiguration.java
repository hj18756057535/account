package com.hypers.account.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.unit.DataSize;

@Configuration
@EnableScheduling
public class UserImportConfiguration {
    @Bean
    @ConditionalOnProperty(name = "account.user-import.enabled", havingValue = "true", matchIfMissing = true)
    public MultipartConfigElement userImportMultipartConfig() {
        var factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(5));
        factory.setMaxRequestSize(DataSize.ofMegabytes(6));
        return factory.createMultipartConfig();
    }
}
