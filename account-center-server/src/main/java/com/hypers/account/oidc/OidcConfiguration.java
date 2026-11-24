package com.hypers.account.oidc;

import com.hypers.account.app.AccountStore;
import com.hypers.account.auth.AccountLoginService;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.mapper.OidcAuthorizationMapper;
import com.hypers.account.web.AuthController;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

@Configuration
@ConditionalOnProperty(name = "account.oidc.enabled", havingValue = "true")
@EnableConfigurationProperties(OidcProperties.class)
public class OidcConfiguration {
    @Bean
    public OidcClientRepository oidcClients(AccountStore store, OidcProperties properties) {
        OidcUrlPolicy.validateIssuer(properties.getIssuer(), properties.isAllowIntranetHttp());
        if (properties.getCodeTtl().compareTo(Duration.ofSeconds(30)) < 0
                || properties.getCodeTtl().compareTo(Duration.ofSeconds(120)) > 0
                || properties.getAccessTokenTtl().compareTo(Duration.ofMinutes(1)) < 0
                || properties.getAccessTokenTtl().compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("OIDC token lifetimes are outside the supported range");
        }
        properties.getClients().values().forEach(client -> {
            client.getPostLogoutRedirectUris().forEach(uri ->
                    OidcUrlPolicy.validateWebUri(uri, properties.isAllowIntranetHttp()));
        });
        return new OidcClientRepository(store, properties);
    }

    @Bean
    public OidcAuthorizationStore oidcAuthorizationStore(OidcAuthorizationMapper mapper) {
        return new MyBatisOidcAuthorizationStore(mapper);
    }

    @Bean
    public OAuth2AuthorizationService oidcAuthorizations(OidcAuthorizationStore store,
                                                         RegisteredClientRepository clients,
                                                         OidcAccessPolicy policy, Clock clock) {
        return new PersistentOidcAuthorizationService(store, clients, policy, clock);
    }

    @Bean
    public OidcAccessPolicy oidcAccessPolicy(AccountStore store) {
        return new OidcAccessPolicy(store);
    }

    @Bean
    public JWKSource<SecurityContext> oidcKeys(OidcProperties properties, ResourceLoader resources) {
        try {
            KeyStore keys = KeyStore.getInstance("PKCS12");
            char[] password = properties.getKeyStorePassword().toCharArray();
            try (var input = resources.getResource(properties.getKeyStore()).getInputStream()) {
                keys.load(input, password);
            }
            var signingAlias = properties.getSigningKeyAlias();
            var privateKey = (RSAPrivateKey) keys.getKey(signingAlias, password);
            var publicKey = (RSAPublicKey) keys.getCertificate(signingAlias).getPublicKey();
            if (privateKey == null || publicKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException();
            }
            List<JWK> jwks = new ArrayList<>();
            jwks.add(new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(signingAlias).build());
            for (var aliases = keys.aliases(); aliases.hasMoreElements();) {
                var alias = aliases.nextElement();
                if (!alias.equals(signingAlias)
                        && keys.getCertificate(alias).getPublicKey() instanceof RSAPublicKey historicalKey) {
                    jwks.add(new RSAKey.Builder(historicalKey).keyID(alias).build());
                }
            }
            var set = new JWKSet(jwks);
            return (selector, context) -> selector.select(set);
        } catch (Exception exception) {
            // 不把文件路径、密码或私钥读取异常写进启动日志。
            throw new IllegalStateException("OIDC requires a valid persistent PKCS12 RSA signing key");
        }
    }

    @Bean
    public JwtDecoder oidcJwtDecoder(JWKSource<SecurityContext> keys, OidcProperties properties) {
        var decoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(keys);
        var issuerValidator = new JwtIssuerValidator(properties.getIssuer());
        return token -> {
            var jwt = decoder.decode(token);
            var validation = issuerValidator.validate(jwt);
            if (validation.hasErrors()) {
                throw new JwtValidationException("Unexpected token issuer", validation.getErrors());
            }
            return jwt;
        };
    }

    @Bean
    public AuthorizationServerSettings oidcServerSettings(OidcProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.getIssuer()).build();
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> oidcClaims(AccountStore store, OidcProperties properties) {
        return context -> {
            context.getJwsHeader().keyId(properties.getSigningKeyAlias());
            if ("id_token".equals(context.getTokenType().getValue())
                    && context.getAuthorizedScopes().contains("profile")) {
                var user = store.requireUser(context.getPrincipal().getName());
                context.getClaims().claim("name", user.getName()).claim("preferred_username", user.getAccount());
            }
        };
    }

    @Bean
    public AuthenticationProvider accountOidcLoginProvider(AccountLoginService login) {
        return new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                try {
                    var identity = login.authenticate(authentication.getName(), String.valueOf(authentication.getCredentials()));
                    var user = User.withUsername(identity.getUserId()).password("").authorities("ACCOUNT_USER").build();
                    return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
                } catch (IllegalArgumentException exception) {
                    throw new BadCredentialsException("Invalid credentials");
                }
            }

            @Override
            public boolean supports(Class<?> type) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
            }
        };
    }

    @Bean
    @Order(1)
    public SecurityFilterChain oidcProtocolSecurity(HttpSecurity http, AccountStore store,
                                                    OidcAccessPolicy policy, OAuth2AuthorizationService authorizations)
            throws Exception {
        var server = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(server.getEndpointsMatcher())
                .with(server, authorizationServer -> authorizationServer
                        .authorizationServerMetadataEndpoint(endpoint -> endpoint
                                .authorizationServerMetadataCustomizer(builder -> builder
                                        .codeChallengeMethods(methods -> {
                                            methods.clear();
                                            methods.add("S256");
                                        })
                                        .grantTypes(grants -> {
                                            grants.clear();
                                            grants.add("authorization_code");
                                        })
                                        .tokenEndpointAuthenticationMethods(methods -> {
                                            methods.clear();
                                            methods.add("client_secret_basic");
                                        }).scope("profile")))
                        .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers ->
                                providers.forEach(provider -> {
                                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider code) {
                                        code.setAuthenticationValidator(
                                                OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR
                                                        .andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR)
                                                        .andThen(context -> {
                                                            var request = (OAuth2AuthorizationCodeRequestAuthenticationToken) context.getAuthentication();
                                                            var parameters = request.getAdditionalParameters();
                                                            if (!request.getScopes().contains("openid")
                                                                    || !"S256".equals(parameters.get("code_challenge_method"))) {
                                                                throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                                                                        new OAuth2Error("invalid_request"), request);
                                                            }
                                                            if (request.getPrincipal() instanceof Authentication principal
                                                                    && principal.isAuthenticated()
                                                                    && !"anonymousUser".equals(principal.getName())
                                                                    && !policy.allows(principal.getName(), request.getClientId())) {
                                                                throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                                                                        new OAuth2Error("access_denied"), request);
                                                            }
                                                        }));
                                    }
                                })))
                        .tokenEndpoint(endpoint -> endpoint.authenticationProviders(providers ->
                                providers.replaceAll(provider ->
                                        provider instanceof OAuth2AuthorizationCodeAuthenticationProvider
                                                ? new OidcCodeAuthenticationProvider(provider, authorizations, policy) : provider)))
                        .oidc(oidc -> oidc.providerConfigurationEndpoint(endpoint -> endpoint
                                        .providerConfigurationCustomizer(builder -> builder
                                                .codeChallengeMethods(methods -> {
                                                    methods.clear();
                                                    methods.add("S256");
                                                })
                                                .grantTypes(grants -> {
                                                    grants.clear();
                                                    grants.add("authorization_code");
                                                })
                                                .tokenEndpointAuthenticationMethods(methods -> {
                                                    methods.clear();
                                                    methods.add("client_secret_basic");
                                                }).scope("profile")))
                                .userInfoEndpoint(endpoint -> endpoint.userInfoMapper(context -> {
                                    var authorization = context.getAuthorization();
                                    policy.require(authorization.getPrincipalName(), authorization.getRegisteredClientId(), "invalid_token");
                                    var user = store.requireUser(authorization.getPrincipalName());
                                    if (authorization.getAuthorizedScopes().contains("profile")) {
                                        return new OidcUserInfo(Map.of("sub", user.getId(), "name", user.getName(),
                                                "preferred_username", user.getAccount()));
                                    }
                                    return new OidcUserInfo(Map.of("sub", user.getId()));
                                }))))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) -> {
                    if (request.getRequestURI().equals(request.getContextPath() + "/oauth2/authorize")) {
                        new LoginUrlAuthenticationEntryPoint("/login").commence(request, response, exception);
                    } else {
                        new BearerTokenAuthenticationEntryPoint().commence(request, response, exception);
                    }
                }))
                .oauth2ResourceServer(resource -> resource.jwt(Customizer.withDefaults()))
                .addFilterAfter(new OidcSessionFilter(store), SecurityContextHolderFilter.class);
        var cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(request -> "/oauth2/authorize".equals(request.getServletPath())
                || request.getRequestURI().equals(request.getContextPath() + "/oauth2/authorize"));
        http.requestCache(requests -> requests.requestCache(cache));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain oidcApplicationSecurity(HttpSecurity http, AccountStore store,
                                                      AuthenticationProvider accountOidcLoginProvider) throws Exception {
        var success = new SavedRequestAwareAuthenticationSuccessHandler();
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .authenticationProvider(accountOidcLoginProvider)
                .formLogin(form -> form.loginPage("/login").usernameParameter("account")
                        .successHandler((request, response, authentication) -> {
                            var user = store.requireUser(authentication.getName());
                            var session = request.getSession();
                            session.setAttribute(AuthController.SESSION_USER_KEY,
                                    new AccountSessionUser(user.getId(), user.getAccount(), user.getName()));
                            var legacyRedirect = session.getAttribute("loginRedirectUrl");
                            session.removeAttribute("loginRedirectUrl");
                            if (legacyRedirect instanceof String location && location.startsWith("/sso/authorize?")) {
                                response.sendRedirect(request.getContextPath() + location);
                            } else {
                                success.onAuthenticationSuccess(request, response, authentication);
                            }
                        }).failureUrl("/login?error"))
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request ->
                        CsrfFilter.DEFAULT_CSRF_MATCHER.matches(request)
                                && (request.getRequestURI().equals(request.getContextPath() + "/login")
                                || request.getRequestURI().equals(request.getContextPath() + "/logout"))))
                .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login"))
                .addFilterAfter(new OidcSessionFilter(store), SecurityContextHolderFilter.class);
        return http.build();
    }
}
