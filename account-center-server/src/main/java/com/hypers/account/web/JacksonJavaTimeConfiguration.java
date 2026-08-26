package com.hypers.account.web;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonJavaTimeConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer accountJavaTimeCustomizer() {
        return builder -> builder.modulesToInstall(JavaTimeModule.class);
    }
}
