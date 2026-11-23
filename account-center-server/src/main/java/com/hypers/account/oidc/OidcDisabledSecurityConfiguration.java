package com.hypers.account.oidc;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "account.oidc.enabled", havingValue = "false", matchIfMissing = true)
public class OidcDisabledSecurityConfiguration {
    /** 未启用 OIDC 时保留已有 MVC Session、CSRF 与 HMAC 安全边界。 */
    @Bean
    public SecurityFilterChain existingAccountSecurity(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable());
        return http.build();
    }
}
