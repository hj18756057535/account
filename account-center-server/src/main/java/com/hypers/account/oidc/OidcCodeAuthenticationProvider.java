package com.hypers.account.oidc;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationToken;

/** 兑换时重新检查准入；官方 Provider 继续负责客户端、PKCE、回调和令牌生成。 */
@RequiredArgsConstructor
final class OidcCodeAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider delegate;
    private final OAuth2AuthorizationService authorizations;
    private final OidcAccessPolicy policy;

    @Override
    public Authentication authenticate(Authentication authentication) {
        var request = (OAuth2AuthorizationCodeAuthenticationToken) authentication;
        var authorization = authorizations.findByToken(request.getCode(), new OAuth2TokenType("code"));
        if (authorization == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"));
        }
        policy.require(authorization.getPrincipalName(), authorization.getRegisteredClientId(), "invalid_grant");
        return delegate.authenticate(authentication);
    }

    @Override
    public boolean supports(Class<?> type) {
        return delegate.supports(type);
    }
}
