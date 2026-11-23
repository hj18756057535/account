package com.hypers.account.oidc;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

/** 应用主数据仍由 Account 管理，配置仅提供 OIDC 专用密钥和退出回调。 */
@RequiredArgsConstructor
public class OidcClientRepository implements RegisteredClientRepository {
    private final AccountStore store;
    private final OidcProperties properties;

    @Override
    public void save(RegisteredClient client) {
        throw new UnsupportedOperationException("Manage OIDC clients through Account applications and configuration");
    }

    @Override
    public RegisteredClient findById(String id) {
        return findByClientId(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        var settings = properties.getClients().get(clientId);
        if (settings == null) return null;
        AccountApplication application;
        try {
            application = store.requireApplication(clientId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (!"enabled".equals(application.getStatus())) return null;
        OidcUrlPolicy.validateWebUri(application.getSsoCallbackUrl(), properties.isAllowIntranetHttp());
        var builder = RegisteredClient.withId(clientId).clientId(clientId).clientName(application.getName())
                .clientSecret(settings.getSecretHash())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(application.getSsoCallbackUrl())
                .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().authorizationCodeTimeToLive(properties.getCodeTtl())
                        .accessTokenTimeToLive(properties.getAccessTokenTtl()).build());
        settings.getPostLogoutRedirectUris().forEach(uri -> {
            OidcUrlPolicy.validateWebUri(uri, properties.isAllowIntranetHttp());
            builder.postLogoutRedirectUri(uri);
        });
        return builder.build();
    }

}
