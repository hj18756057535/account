package com.hypers.account.oidc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import com.hypers.account.mapper.OidcAuthorizationMapper;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

/** 官方持久化 SPI 适配；载荷仅从内部数据库读取，不能由 HTTP 请求提交。 */
public class PersistentOidcAuthorizationService implements OAuth2AuthorizationService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final OidcAuthorizationMapper authorizationMapper;
    private final RegisteredClientRepository clients;
    private final OidcAccessPolicy policy;
    private final Clock clock;
    private final ObjectMapper documents = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ObjectMapper security = new ObjectMapper();

    public PersistentOidcAuthorizationService(OidcAuthorizationMapper authorizationMapper,
                                              RegisteredClientRepository clients, OidcAccessPolicy policy, Clock clock) {
        this.authorizationMapper = authorizationMapper;
        this.clients = clients;
        this.policy = policy;
        this.clock = clock;
        security.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        security.registerModule(new OAuth2AuthorizationServerJackson2Module());
        security.registerModule(new JavaTimeModule());
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        try {
            var payload = new Payload();
            payload.setGrantType(authorization.getAuthorizationGrantType().getValue());
            payload.setScopes(authorization.getAuthorizedScopes());
            payload.setAttributes(security.writeValueAsString(authorization.getAttributes()));
            payload.setCode(token(authorization.getToken(OAuth2AuthorizationCode.class)));
            payload.setAccessToken(token(authorization.getAccessToken()));
            payload.setIdToken(token(authorization.getToken(OidcIdToken.class)));
            var record = new OidcAuthorizationRecord();
            record.setId(authorization.getId());
            record.setClientId(authorization.getRegisteredClientId());
            record.setPrincipalName(authorization.getPrincipalName());
            record.setPayload(documents.writeValueAsString(payload));
            record.setStateHash(hash(authorization.getAttribute("state")));
            record.setCodeHash(hash(payload.getCode() == null ? null : payload.getCode().getValue()));
            record.setAccessTokenHash(hash(payload.getAccessToken() == null ? null : payload.getAccessToken().getValue()));
            record.setIdTokenHash(hash(payload.getIdToken() == null ? null : payload.getIdToken().getValue()));
            var code = authorization.getToken(OAuth2AuthorizationCode.class);
            record.setCodeRedeemed(code != null && code.isInvalidated());
            record.setCreatedAt(clock.instant());
            record.setUpdatedAt(clock.instant());
            save(record);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to persist OIDC authorization");
        }
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        authorizationMapper.delete(authorization.getId());
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return restore(authorizationMapper.findById(id));
    }

    @Override
    public OAuth2Authorization findByToken(String value, OAuth2TokenType type) {
        if (value == null || value.isBlank()) return null;
        String tokenType = type == null ? null : type.getValue();
        if (tokenType != null && !Set.of("state", "code", "access_token", "id_token").contains(tokenType)) return null;
        String digest = hash(value);
        var record = authorizationMapper.findByTokenHash(digest, tokenType);
        if (record != null && digest.equals(record.getAccessTokenHash())
                && !policy.allows(record.getPrincipalName(), record.getClientId())) return null;
        return restore(record);
    }

    private OAuth2Authorization restore(OidcAuthorizationRecord record) {
        if (record == null) return null;
        var client = clients.findById(record.getClientId());
        if (client == null) return null;
        try {
            var payload = documents.readValue(record.getPayload(), Payload.class);
            var builder = OAuth2Authorization.withRegisteredClient(client).id(record.getId())
                    .principalName(record.getPrincipalName())
                    .authorizationGrantType(new AuthorizationGrantType(payload.getGrantType()))
                    .authorizedScopes(payload.getScopes())
                    .attributes(attributes -> attributes.putAll(readMap(payload.getAttributes())));
            var code = payload.getCode();
            if (code != null) {
                builder.token(new OAuth2AuthorizationCode(code.getValue(), code.getIssuedAt(), code.getExpiresAt()),
                        metadata -> metadata.putAll(readMap(code.getMetadata())));
            }
            var access = payload.getAccessToken();
            if (access != null) {
                builder.token(new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                        access.getValue(), access.getIssuedAt(), access.getExpiresAt(), payload.getScopes()),
                        metadata -> metadata.putAll(readMap(access.getMetadata())));
            }
            var id = payload.getIdToken();
            if (id != null) {
                var metadata = readMap(id.getMetadata());
                @SuppressWarnings("unchecked")
                var claims = (Map<String, Object>) metadata.get(OAuth2Authorization.Token.CLAIMS_METADATA_NAME);
                builder.token(new OidcIdToken(id.getValue(), id.getIssuedAt(), id.getExpiresAt(), claims),
                        target -> target.putAll(metadata));
            }
            return builder.build();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read OIDC authorization");
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return security.readValue(json, MAP_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException("Invalid persisted OIDC metadata");
        }
    }

    private TokenData token(OAuth2Authorization.Token<? extends OAuth2Token> source) throws IOException {
        if (source == null) return null;
        var data = new TokenData();
        data.setValue(source.getToken().getTokenValue());
        data.setIssuedAt(source.getToken().getIssuedAt());
        data.setExpiresAt(source.getToken().getExpiresAt());
        data.setMetadata(security.writeValueAsString(source.getMetadata()));
        return data;
    }

    static String hash(String value) {
        if (value == null) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    @Getter
    @Setter
    public static class Payload {
        private String grantType;
        private Set<String> scopes;
        private String attributes;
        private TokenData code;
        private TokenData accessToken;
        private TokenData idToken;
    }

    @Getter
    @Setter
    public static class TokenData {
        private String value;
        private Instant issuedAt;
        private Instant expiresAt;
        private String metadata;
    }

    /** 原子条件更新保证同一授权码只有一次兑换结果能提交。 */
    private void save(OidcAuthorizationRecord record) {
        if (authorizationMapper.findById(record.getId()) == null) {
            if (record.isCodeRedeemed()) {
                throw new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"));
            }
            authorizationMapper.insert(record);
        } else if (authorizationMapper.update(record) != 1) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_grant"));
        }
    }
}
