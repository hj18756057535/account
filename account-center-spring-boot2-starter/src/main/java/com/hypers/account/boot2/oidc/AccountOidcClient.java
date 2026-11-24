package com.hypers.account.boot2.oidc;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.Assert;
import org.springframework.web.client.RestTemplate;

final class AccountOidcClient {

    private static final String REGISTRATION_ID = "account";
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64StringKeyGenerator VERIFIERS = new Base64StringKeyGenerator(BASE64_URL, 32);

    private final ClientRegistration registration;
    private final DefaultOAuth2AuthorizationRequestResolver resolver;
    private final OidcAuthorizationCodeAuthenticationProvider provider;

    AccountOidcClient(AccountOidcProperties properties) {
        this(ClientRegistrations.fromOidcIssuerLocation(properties.getIssuer().toString())
                .registrationId(REGISTRATION_ID)
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(properties.getRedirectUri().toString())
                .scope("openid", "profile")
                .build());
    }

    private AccountOidcClient(ClientRegistration registration) {
        this.registration = registration;
        resolver = new DefaultOAuth2AuthorizationRequestResolver(
                new InMemoryClientRegistrationRepository(registration), "/login");
        // Spring Security 5.6 only adds PKCE by default for public clients.
        resolver.setAuthorizationRequestCustomizer(builder -> {
            String verifier = VERIFIERS.generateKey();
            builder.attributes(attributes -> attributes.put(PkceParameterNames.CODE_VERIFIER, verifier));
            builder.additionalParameters(parameters -> {
                parameters.put(PkceParameterNames.CODE_CHALLENGE, challenge(verifier));
                parameters.put(PkceParameterNames.CODE_CHALLENGE_METHOD, "S256");
            });
        });
        provider = new OidcAuthorizationCodeAuthenticationProvider(tokenClient(),
                request -> new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, request.getIdToken()));
    }

    OAuth2AuthorizationRequest authorizationRequest(HttpServletRequest request) {
        return resolver.resolve(request, REGISTRATION_ID);
    }

    AccountOidcIdentity authenticate(AuthorizationProof proof, String state, String code) {
        Assert.hasText(proof.getNonce(), "OIDC nonce is required");
        Assert.hasText(proof.getVerifier(), "PKCE verifier is required");
        OAuth2AuthorizationRequest authorization = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(registration.getProviderDetails().getAuthorizationUri())
                .clientId(registration.getClientId())
                .scopes(registration.getScopes())
                .redirectUri(registration.getRedirectUri())
                .state(state)
                .attributes(attributes -> {
                    attributes.put(OidcParameterNames.NONCE, proof.getNonce());
                    attributes.put(PkceParameterNames.CODE_VERIFIER, proof.getVerifier());
                })
                .build();
        OAuth2AuthorizationResponse response = OAuth2AuthorizationResponse.success(code)
                .redirectUri(registration.getRedirectUri()).state(state).build();
        OAuth2LoginAuthenticationToken authentication = (OAuth2LoginAuthenticationToken) provider.authenticate(
                new OAuth2LoginAuthenticationToken(registration, new OAuth2AuthorizationExchange(authorization, response)));
        OidcUser user = (OidcUser) authentication.getPrincipal();
        String subject = user.getSubject();
        Assert.hasText(subject, "OIDC subject is required");
        return new AccountOidcIdentity(registration.getProviderDetails().getIssuerUri(),
                subject, user.getPreferredUsername(), user.getFullName());
    }

    private static DefaultAuthorizationCodeTokenResponseClient tokenClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        RestTemplate rest = new RestTemplate(Arrays.asList(new FormHttpMessageConverter(),
                new OAuth2AccessTokenResponseHttpMessageConverter()));
        rest.setRequestFactory(factory);
        rest.setErrorHandler(new OAuth2ErrorResponseErrorHandler());
        DefaultAuthorizationCodeTokenResponseClient client = new DefaultAuthorizationCodeTokenResponseClient();
        client.setRestOperations(rest);
        return client;
    }

    private static String challenge(String verifier) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return BASE64_URL.encodeToString(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class AuthorizationProof {
        private String nonce;
        private String verifier;
    }
}
