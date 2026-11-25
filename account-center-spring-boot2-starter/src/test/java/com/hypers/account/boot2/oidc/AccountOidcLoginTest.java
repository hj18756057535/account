package com.hypers.account.boot2.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

class AccountOidcLoginTest {

    private static final String CMS_ORIGIN = "https://cms.example.test";
    private static final String CLIENT = "cms-ai";
    private static final String CLIENT_SECRET = "fixture-secret";
    private static final String SUBJECT = "account-user-id";
    private static RSAKey signingKey;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final AtomicInteger tokenRequests = new AtomicInteger();
    private HttpServer issuerServer;
    private AccountOidcProperties properties;
    private AccountOidcLogin login;
    private String issuer;
    private volatile String nonce;
    private volatile String audience = CLIENT;
    private volatile String tokenRequestBody;
    private volatile String tokenRequestAuthorization;

    @BeforeAll
    static void createKey() throws JOSEException {
        signingKey = new RSAKeyGenerator(2048).keyID("fixture").generate();
    }

    @BeforeEach
    void startIssuer() throws IOException {
        issuerServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issuer = "http://127.0.0.1:" + issuerServer.getAddress().getPort();
        issuerServer.createContext("/.well-known/openid-configuration", this::metadata);
        issuerServer.createContext("/jwks", exchange -> json(exchange, new JWKSet(signingKey.toPublicJWK()).toJSONObject()));
        issuerServer.createContext("/token", this::issueToken);
        issuerServer.start();
        properties = new AccountOidcProperties();
        properties.setIssuer(URI.create(issuer));
        properties.setClientId(CLIENT);
        properties.setClientSecret(CLIENT_SECRET);
        properties.setRedirectUri(URI.create(CMS_ORIGIN + "/api/main" + AccountOidcLogin.CALLBACK_PATH));
        properties.setLoginPage(URI.create(CMS_ORIGIN + "/login"));
        login = new AccountOidcLogin(properties, fakeRedis(), mapper);
    }

    @AfterEach
    void stopIssuer() {
        if (issuerServer != null) {
            issuerServer.stop(0);
        }
    }

    @Test
    void signedOidcLoginUsesBasicPkceAndConsumesBothBrowserCodesOnce() throws Exception {
        MockHttpServletResponse start = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AccountOidcLogin.START_PATH);
        URI authorization = login.begin(request, start);
        String state = parameter(authorization, "state");
        nonce = parameter(authorization, "nonce");
        assertThat(parameter(authorization, "code_challenge_method")).isEqualTo("S256");
        assertThat(parameter(authorization, "redirect_uri")).isEqualTo(properties.getRedirectUri().toString());
        assertThat(start.getHeaders(HttpHeaders.SET_COOKIE)).allSatisfy(cookie -> assertThat(cookie)
                .contains("__Host-AccountOidc-", "Secure", "HttpOnly", "SameSite=Lax"));
        assertThat(nonce).isNotBlank();

        MockHttpServletRequest callback = callback(state, cookie(start, "-request"));
        MockHttpServletResponse completed = new MockHttpServletResponse();
        AccountOidcLogin anotherInstance = new AccountOidcLogin(properties, fakeRedis(), mapper);
        assertThat(anotherInstance.complete(callback, completed)).isTrue();
        assertThat(login.complete(callback, new MockHttpServletResponse())).isFalse();
        assertThat(tokenRequests.get()).isEqualTo(1);
        assertThat(tokenRequestAuthorization).isEqualTo("Basic " + Base64.getEncoder().encodeToString(
                (CLIENT + ":" + CLIENT_SECRET).getBytes(StandardCharsets.US_ASCII)));
        String verifier = parameter(URI.create("http://localhost/?" + tokenRequestBody), "code_verifier");
        byte[] challenge = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(challenge))
                .isEqualTo(parameter(authorization, "code_challenge"));

        MockHttpServletRequest redeem = new MockHttpServletRequest("POST", AccountOidcLogin.EXCHANGE_PATH);
        redeem.setCookies(cookie(completed, "-result"));
        redeem.addHeader(HttpHeaders.ORIGIN, "https://other.example.test");
        assertThat(login.consumeIdentity(redeem, new MockHttpServletResponse())).isNull();
        redeem = new MockHttpServletRequest("POST", AccountOidcLogin.EXCHANGE_PATH);
        redeem.setCookies(cookie(completed, "-result"));
        redeem.addHeader(HttpHeaders.ORIGIN, CMS_ORIGIN);
        AccountOidcIdentity identity = login.consumeIdentity(redeem, new MockHttpServletResponse());
        assertThat(identity.getSubject()).isEqualTo(SUBJECT);
        assertThat(identity.getIssuer()).isEqualTo(issuer);
        assertThat(identity.getAccount()).isEqualTo("fixture-account");
        assertThat(identity.getDisplayName()).isEqualTo("Fixture User");
        assertThat(anotherInstance.consumeIdentity(redeem, new MockHttpServletResponse())).isNull();
    }

    @Test
    void callbackRequiresTheOriginalStateAndBrowserBeforeTokenExchange() {
        MockHttpServletResponse start = new MockHttpServletResponse();
        URI authorization = login.begin(new MockHttpServletRequest("GET", AccountOidcLogin.START_PATH), start);
        String state = parameter(authorization, "state");
        assertThat(login.complete(callback(state, null), new MockHttpServletResponse())).isFalse();
        assertThat(login.complete(callback(state.replaceFirst("^[A-Za-z]", "0") + "x",
                cookie(start, "-request")), new MockHttpServletResponse())).isFalse();
        assertThat(tokenRequests.get()).isZero();
    }

    @Test
    void rejectsMalformedIdentityResults() {
        MockHttpServletResponse start = new MockHttpServletResponse();
        URI authorization = login.begin(new MockHttpServletRequest("GET", AccountOidcLogin.START_PATH), start);
        nonce = parameter(authorization, "nonce");
        MockHttpServletResponse completed = new MockHttpServletResponse();
        assertThat(login.complete(callback(parameter(authorization, "state"), cookie(start, "-request")), completed)).isTrue();
        values.replaceAll((key, value) -> key.contains(":result:") ? SUBJECT : value);
        MockHttpServletRequest redeem = new MockHttpServletRequest("POST", AccountOidcLogin.EXCHANGE_PATH);
        redeem.setCookies(cookie(completed, "-result"));
        redeem.addHeader(HttpHeaders.ORIGIN, CMS_ORIGIN);
        assertThatThrownBy(() -> login.consumeIdentity(redeem, new MockHttpServletResponse()))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nonce", "audience"})
    void authenticatedCallbackRejectsMismatchedIdTokenClaim(String mismatch) {
        MockHttpServletResponse start = new MockHttpServletResponse();
        URI authorization = login.begin(new MockHttpServletRequest("GET", AccountOidcLogin.START_PATH), start);
        nonce = parameter(authorization, "nonce");
        if ("nonce".equals(mismatch)) {
            nonce = "unrelated-nonce";
        } else {
            audience = "another-client";
        }
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        AccountOidcLoginController controller = new AccountOidcLoginController(login);
        assertThat(controller.callback(callback(parameter(authorization, "state"), cookie(start, "-request")), rejected)
                .getHeaders().getLocation()).isEqualTo(URI.create(CMS_ORIGIN + "/login?accountSso=failed"));
        assertThat(rejected.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(cookie -> cookie.contains("-result"));
    }

    @Test
    void autoConfigurationRegistersEndpointsOnlyWhenEnabled() {
        WebApplicationContextRunner runner = new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AccountOidcAutoConfiguration.class))
                .withBean(StringRedisTemplate.class, this::fakeRedis)
                .withBean(ObjectMapper.class, () -> mapper);
        runner.run(context -> assertThat(context).doesNotHaveBean(AccountOidcLoginController.class));
        runner.withPropertyValues("account.integration.oidc.enabled=true",
                "account.integration.oidc.issuer=" + issuer,
                "account.integration.oidc.client-id=" + CLIENT,
                "account.integration.oidc.client-secret=" + CLIENT_SECRET,
                "account.integration.oidc.redirect-uri=" + properties.getRedirectUri(),
                "account.integration.oidc.login-page=" + properties.getLoginPage())
                .run(context -> assertThat(context).hasSingleBean(AccountOidcLoginController.class));
    }

    private static MockHttpServletRequest callback(String state, Cookie browser) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", AccountOidcLogin.CALLBACK_PATH);
        request.setParameter("state", state);
        request.setParameter("code", "fixture-code");
        if (browser != null) {
            request.setCookies(browser);
        }
        return request;
    }

    private static String parameter(URI uri, String name) {
        String value = UriComponentsBuilder.fromUri(uri).build(true).getQueryParams().getFirst(name);
        return value == null ? null : UriUtils.decode(value, StandardCharsets.UTF_8);
    }

    private static Cookie cookie(MockHttpServletResponse response, String suffix) {
        for (Cookie cookie : response.getCookies()) {
            if (cookie.getName().endsWith(suffix)) {
                return cookie;
            }
        }
        throw new AssertionError("Expected OIDC cookie");
    }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate fakeRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doAnswer(invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        when(redis.execute(any(DefaultRedisScript.class), anyList())).thenAnswer(invocation -> {
            List<String> keys = invocation.getArgument(1);
            return values.remove(keys.get(0));
        });
        return redis;
    }

    private void metadata(HttpExchange exchange) throws IOException {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/authorize");
        metadata.put("token_endpoint", issuer + "/token");
        metadata.put("jwks_uri", issuer + "/jwks");
        metadata.put("response_types_supported", Collections.singletonList("code"));
        metadata.put("subject_types_supported", Collections.singletonList("public"));
        metadata.put("id_token_signing_alg_values_supported", Collections.singletonList("RS256"));
        metadata.put("token_endpoint_auth_methods_supported", Collections.singletonList("client_secret_basic"));
        json(exchange, metadata);
    }

    private void issueToken(HttpExchange exchange) throws IOException {
        tokenRequests.incrementAndGet();
        tokenRequestAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
        tokenRequestBody = StreamUtils.copyToString(exchange.getRequestBody(), StandardCharsets.UTF_8);
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT).keyID(signingKey.getKeyID()).build(),
                new JWTClaimsSet.Builder().issuer(issuer).subject(SUBJECT).audience(audience).claim("nonce", nonce)
                        .claim("preferred_username", "fixture-account").claim("name", "Fixture User")
                        .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(120))).build());
        try {
            jwt.sign(new RSASSASigner(signingKey));
        } catch (JOSEException exception) {
            throw new IOException(exception);
        }
        Map<String, Object> tokens = new LinkedHashMap<>();
        tokens.put("access_token", "fixture-access-token");
        tokens.put("token_type", "Bearer");
        tokens.put("scope", "openid profile");
        tokens.put("id_token", jwt.serialize());
        tokens.put("expires_in", 120);
        json(exchange, tokens);
    }

    private void json(HttpExchange exchange, Object body) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try {
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }
}
