package com.hypers.account.oidc;

import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.service.ApplicationManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 页面登记的应用与当前 Secret 是 OIDC 客户端的事实源。
 * 只读取应用主数据，不单独维护一份 OIDC 客户端表。
 */
@RequiredArgsConstructor
public class OidcClientRepository implements RegisteredClientRepository {
    private final ApplicationManagementService applicationService;
    private final OidcProperties properties;

    @Override
    public void save(RegisteredClient client) {
        throw new UnsupportedOperationException("Manage OIDC clients through Account applications");
    }

    @Override
    public RegisteredClient findById(String id) {
        return findByClientId(id);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        var settings = properties.getClients().get(clientId);
        AccountApplication application;
        try {
            application = applicationService.getApplication(clientId);
        } catch (BusinessException exception) {
            return null;
        }
        if (!"enabled".equals(application.getStatus()) || !"active".equals(application.getSecretState())
                || application.getSecret() == null || application.getSecret().isBlank()
                || !application.hasSsoConfiguration()) return null;
        OidcUrlPolicy.validateWebUri(application.getSsoCallbackUrl(), properties.isAllowIntranetHttp());
        var builder = RegisteredClient.withId(clientId).clientId(clientId).clientName(application.getName())
                .clientSecret("{bcrypt}" + new BCryptPasswordEncoder().encode(application.getSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(application.getSsoCallbackUrl())
                .scope(OidcScopes.OPENID).scope(OidcScopes.PROFILE)
                .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().authorizationCodeTimeToLive(properties.getCodeTtl())
                        .accessTokenTimeToLive(properties.getAccessTokenTtl()).build());
        if (settings != null) settings.getPostLogoutRedirectUris().forEach(uri -> {
            OidcUrlPolicy.validateWebUri(uri, properties.isAllowIntranetHttp());
            builder.postLogoutRedirectUri(uri);
        });
        return builder.build();
    }

}
