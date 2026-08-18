package com.hypers.account.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hypers.account.app.AccountApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonSecurityConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer accountApplicationSecretCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.setMixInAnnotation(AccountApplication.class, AccountApplicationSecretMixin.class);
            builder.modules(module);
        };
    }

    abstract static class AccountApplicationSecretMixin {

        @JsonIgnore
        abstract String getSecret();
    }
}
