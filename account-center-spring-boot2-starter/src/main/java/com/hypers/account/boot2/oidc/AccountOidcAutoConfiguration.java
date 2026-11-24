package com.hypers.account.boot2.oidc;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({OidcAuthorizationCodeAuthenticationProvider.class, NimbusJwtDecoder.class,
        StringRedisTemplate.class})
@ConditionalOnProperty(prefix = "account.integration.oidc", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AccountOidcProperties.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class AccountOidcAutoConfiguration {

    @Bean
    public AccountOidcLogin accountOidcLogin(AccountOidcProperties properties,
                                            StringRedisTemplate redis, ObjectMapper mapper) {
        return new AccountOidcLogin(properties, redis, mapper);
    }

    @Bean
    public AccountOidcLoginController accountOidcLoginController(AccountOidcLogin login) {
        return new AccountOidcLoginController(login);
    }
}
