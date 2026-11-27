package com.hypers.account.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.model.AccountUser;
import com.hypers.account.app.service.ApplicationManagementService;
import com.hypers.account.app.service.UserApplicationAccessService;
import com.hypers.account.app.service.UserManagementService;
import com.hypers.account.app.command.RegisterApplicationCommand;
import com.hypers.account.app.command.SaveUserCommand;
import com.hypers.account.app.command.SaveApplicationCommand;
import com.hypers.account.auth.AccountLoginService;
import com.hypers.account.mapper.OidcAuthorizationMapper;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.web.AuthController;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.junit.jupiter.api.Assertions;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OidcIntegrationTest {
    private static final String ISSUER = System.getProperty("oidc.test.issuer",
            System.getenv().getOrDefault("OIDC_TEST_ISSUER", "http://localhost:8088"));
    private static final String CLIENT = "oidc-agent";
    private static final String CALLBACK = System.getProperty("oidc.test.callback",
            System.getenv().getOrDefault("OIDC_TEST_CALLBACK", "http://localhost:9000/auth/oidc/callback"));
    private static final String CLIENT_SECRET = UUID.randomUUID().toString();
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final String KEY_PASSWORD = UUID.randomUUID().toString();
    private static final String VERIFIER = "test-verifier-0123456789012345678901234567890123456789";
    private static final String NONCE = "test-browser-login-nonce";
    private static final Path KEYSTORE = createKeyStore();

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserManagementService userService;
    @Autowired private ApplicationManagementService applicationService;
    @Autowired private UserApplicationAccessService accessService;
    @Autowired private AccountLoginService login;
    @Autowired private OAuth2AuthorizationService authorizations;
    @Autowired private OidcAuthorizationMapper authorizationMapper;
    @Autowired private OidcClientRepository clients;
    @Autowired private OidcProperties properties;
    @Autowired private OidcAccessPolicy policy;
    @Autowired private Clock clock;
    @Autowired private JwtDecoder applicationDecoder;
    @Autowired private JWKSource<SecurityContext> signingKeys;

    private AccountUser user;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:oidc_integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("account.oidc.enabled", () -> "true");
        registry.add("account.oidc.issuer", () -> ISSUER);
        registry.add("account.oidc.allow-intranet-http", () -> Boolean.parseBoolean(System.getProperty(
                "oidc.test.intranet-http", System.getenv().getOrDefault("OIDC_TEST_INTRANET_HTTP", "false"))));
        registry.add("account.oidc.key-store", () -> KEYSTORE.toUri().toString());
        registry.add("account.oidc.key-store-password", () -> KEY_PASSWORD);
        registry.add("account.oidc.signing-key-alias", () -> "test-signing");
        registry.add("account.oidc.clients.oidc-agent.post-logout-redirect-uris[0]",
                () -> "http://localhost:9000/logged-out");
    }

    @BeforeEach
    void registerUserAndApplication() {
        applicationService.registerApplication(new RegisterApplicationCommand(CLIENT, "OIDC Agent",
                "http://localhost:9000", CALLBACK, "http://localhost:9000/permissions",
                "", CLIENT_SECRET, "default"));
        applicationService.changeManagedApplicationStatus(CLIENT, "enabled", applicationService.getApplication(CLIENT).getVersion(), null);
        user = userService.createUser(new SaveUserCommand("oidc-" + UUID.randomUUID(),
                "oidc@example.test", "OIDC Test User", "10000000000"));
        userService.updatePassword(user.getId(), login.encodePassword(PASSWORD));
        accessService.authorize(user.getId(), CLIENT);
    }

    @Test
    void discoveryAndPublicJwksAdvertiseFixedIssuerWithoutPrivateKeys() throws Exception {
        var metadata = body(mvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk()).andReturn());
        assertThat(metadata.path("issuer").asText()).isEqualTo(ISSUER);
        assertThat(metadata.path("authorization_endpoint").asText()).isEqualTo(ISSUER + "/oauth2/authorize");
        assertThat(metadata.path("token_endpoint").asText()).isEqualTo(ISSUER + "/oauth2/token");
        assertThat(metadata.path("end_session_endpoint").asText()).isEqualTo(ISSUER + "/connect/logout");
        assertThat(metadata.path("code_challenge_methods_supported").toString()).isEqualTo("[\"S256\"]");
        assertThat(metadata.path("grant_types_supported").toString()).isEqualTo("[\"authorization_code\"]");
        assertThat(metadata.path("scopes_supported").toString()).contains("openid", "profile");
        var jwks = body(mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk()).andReturn());
        assertThat(jwks.path("keys").get(0).path("kid").asText()).isEqualTo("test-signing");
        assertThat(jwks.path("keys").get(0).has("d")).isFalse();
    }

    @Test
    void firstLoginUsesCsrfAndResumesAuthorizationRequest() throws Exception {
        var first = authorize(null, challenge(VERIFIER), CALLBACK);
        assertThat(first.getResponse().getStatus()).isEqualTo(302);
        assertThat(first.getResponse().getRedirectedUrl()).endsWith("/login");
        var session = (MockHttpSession) first.getRequest().getSession(false);
        var page = mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();
        String csrf = csrf(page);
        mvc.perform(post("/login").session(session).param("account", user.getAccount()).param("password", PASSWORD))
                .andExpect(status().isForbidden());
        var result = mvc.perform(post("/login").session(session)
                        .param("account", user.getAccount()).param("password", PASSWORD).param("_csrf", csrf))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).contains("/oauth2/authorize");
        assertThat(((AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY)).getUserId())
                .isEqualTo(user.getId());
        assertThat(code(authorize(session, challenge(VERIFIER), CALLBACK))).isNotBlank();
    }

    @Test
    void secondAuthorizationAfterAbandonedLoginResumesTheLatestRequest() throws Exception {
        var first = authorize(null, challenge(VERIFIER), CALLBACK, "abandoned-state", "abandoned-nonce");
        var session = (MockHttpSession) first.getRequest().getSession(false);
        var firstPage = mvc.perform(get("/login").session(session)).andExpect(status().isOk()).andReturn();
        String nextVerifier = "second-verifier-0123456789012345678901234567890123456789";
        var second = authorize(session, challenge(nextVerifier), CALLBACK, "second-state", "second-nonce");
        assertThat(second.getResponse().getRedirectedUrl()).endsWith("/login");

        var signedIn = mvc.perform(post("/login").session(session)
                        .param("account", user.getAccount()).param("password", PASSWORD)
                        .param("_csrf", csrf(firstPage)))
                .andExpect(status().is3xxRedirection()).andReturn();
        String resumed = signedIn.getResponse().getRedirectedUrl();
        assertThat(resumed).contains("state=second-state", "nonce=second-nonce");
        var authorized = mvc.perform(get(URI.create(resumed)).session(session)).andReturn();
        var tokens = exchange(code(authorized), nextVerifier, CLIENT_SECRET);
        assertThat(tokens.getResponse().getStatus()).isEqualTo(200);
        assertThat(decoder().decode(body(tokens).path("id_token").asText()).getClaimAsString("nonce"))
                .isEqualTo("second-nonce");
    }

    @Test
    void authorizationCodeAndUserInfoUseStableGlobalSubjectAndProfile() throws Exception {
        var tokens = exchange(code(authorize(session(), challenge(VERIFIER), CALLBACK)), VERIFIER, CLIENT_SECRET);
        assertThat(tokens.getResponse().getStatus()).isEqualTo(200);
        var response = body(tokens);
        var decoder = decoder();
        var id = decoder.decode(response.path("id_token").asText());
        assertThat(id.getSubject()).isEqualTo(user.getId());
        assertThat(id.getAudience()).contains(CLIENT);
        assertThat(id.getClaimAsString("nonce")).isEqualTo(NONCE);
        assertThat(id.getClaimAsString("preferred_username")).isEqualTo(user.getAccount());
        assertThat(id.getClaimAsString("name")).isEqualTo(user.getName());
        assertThat(response.has("refresh_token")).isFalse();
        var profile = body(mvc.perform(get("/userinfo")
                        .header("Authorization", "Bearer " + response.path("access_token").asText()))
                .andExpect(status().isOk()).andReturn());
        assertThat(profile.path("sub").asText()).isEqualTo(id.getSubject());
        assertThat(profile.has("email")).isFalse();
        assertThat(profile.has("phone_number")).isFalse();
    }

    @Test
    void untrustedCallbackNeverReceivesAnAuthorizationCode() throws Exception {
        var result = authorize(session(), challenge(VERIFIER), "http://localhost:9001/attacker");
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getRedirectedUrl()).isNull();
    }

    @Test
    void noApplicationAccessReturnsStandardAccessDenied() throws Exception {
        accessService.deauthorize(user.getId(), CLIENT);
        var response = authorize(session(), challenge(VERIFIER), CALLBACK).getResponse();
        assertThat(response.getRedirectedUrl()).contains("error=access_denied");
        assertThat(response.getRedirectedUrl()).doesNotContain("code=");
    }

    @Test
    void onlyS256PkceAndOpenidScopeAreAccepted() throws Exception {
        var session = session();
        var result = mvc.perform(get("/oauth2/authorize").session(session)
                .queryParam("client_id", CLIENT).queryParam("redirect_uri", CALLBACK).queryParam("response_type", "code")
                .queryParam("scope", "openid profile").queryParam("state", "state")
                .queryParam("code_challenge", VERIFIER).queryParam("code_challenge_method", "plain")).andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).contains("error=invalid_request");
        var missing = mvc.perform(get("/oauth2/authorize").session(session)
                .queryParam("client_id", CLIENT).queryParam("redirect_uri", CALLBACK).queryParam("response_type", "code")
                .queryParam("scope", "openid profile").queryParam("state", "state")).andReturn();
        assertThat(missing.getResponse().getRedirectedUrl()).contains("error=");
    }

    @Test
    void wrongPkceCannotExchangeCode() throws Exception {
        String code = code(authorize(session(), challenge(VERIFIER), CALLBACK));
        assertThat(exchange(code, VERIFIER + "-wrong", CLIENT_SECRET).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void incorrectClientSecretIsRejected() throws Exception {
        String code = code(authorize(session(), challenge(VERIFIER), CALLBACK));
        assertThat(exchange(code, VERIFIER, CLIENT_SECRET + "-wrong").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void revokedAccessCannotExchangePreviouslyIssuedCode() throws Exception {
        String code = code(authorize(session(), challenge(VERIFIER), CALLBACK));
        accessService.deauthorize(user.getId(), CLIENT);
        var result = exchange(code, VERIFIER, CLIENT_SECRET);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(result).path("error").asText()).isEqualTo("invalid_grant");
    }

    @Test
    void revokedAccessCannotUsePreviouslyIssuedAccessToken() throws Exception {
        var tokens = body(exchange(code(authorize(session(), challenge(VERIFIER), CALLBACK)), VERIFIER, CLIENT_SECRET));
        accessService.deauthorize(user.getId(), CLIENT);
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.path("access_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledUserCannotReuseAnExistingAccountSession() throws Exception {
        var session = session();
        userService.changeManagedUserStatus(user.getId(), "disabled", userService.getUser(user.getId()).getVersion(), null);
        var response = authorize(session, challenge(VERIFIER), CALLBACK).getResponse();
        assertThat(response.getRedirectedUrl()).endsWith("/login");
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        String value = code(authorize(session(), challenge(VERIFIER), CALLBACK));
        var authorization = authorizations.findByToken(value, new OAuth2TokenType("code"));
        var expired = new OAuth2AuthorizationCode(value, Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        authorizations.save(OAuth2Authorization.from(authorization).token(expired).build());
        assertThat(exchange(value, VERIFIER, CLIENT_SECRET).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void sequentialReplayIsRejected() throws Exception {
        String value = code(authorize(session(), challenge(VERIFIER), CALLBACK));
        assertThat(exchange(value, VERIFIER, CLIENT_SECRET).getResponse().getStatus()).isEqualTo(200);
        assertThat(exchange(value, VERIFIER, CLIENT_SECRET).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void concurrentExchangeHasOnlyOneSuccessfulResponse() throws Exception {
        String value = code(authorize(session(), challenge(VERIFIER), CALLBACK));
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Callable<Integer>> requests = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                requests.add(() -> exchange(value, VERIFIER, CLIENT_SECRET).getResponse().getStatus());
            }
            var results = executor.invokeAll(requests);
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) statuses.add(result.get());
            assertThat(statuses).filteredOn(status -> status == 200).hasSize(1);
            assertThat(statuses).allMatch(status -> status == 200 || status == 400);
        }
    }

    @Test
    void anotherServiceInstanceRestoresCodeAndSigningKey() throws Exception {
        String value = code(authorize(session(), challenge(VERIFIER), CALLBACK));
        var another = new PersistentOidcAuthorizationService(authorizationMapper, clients, policy, clock);
        assertThat(another.findByToken(value, new OAuth2TokenType("code")).getPrincipalName()).isEqualTo(user.getId());
        var config = new OidcConfiguration();
        var reloaded = config.oidcKeys(properties, new DefaultResourceLoader());
        var selector = new JWKSelector(new JWKMatcher.Builder().keyID("test-signing").build());
        assertThat(reloaded.get(selector, null)).hasSize(1);
        assertThat(((RSAKey) reloaded.get(selector, null).get(0)).toRSAPublicKey())
                .isEqualTo(((RSAKey) JWKSet.parse(mvc.perform(get("/oauth2/jwks")).andReturn()
                        .getResponse().getContentAsString()).getKeys().get(0)).toRSAPublicKey());
    }

    @Test
    void existingManagementSessionEndpointKeepsItsJsonAndCsrfBoundary() throws Exception {
        var result = mvc.perform(get("/api/session")).andExpect(status().isOk()).andReturn();
        assertThat(body(result).path("authenticated").asBoolean()).isFalse();
        mvc.perform(post("/api/session").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"account\":\"test\",\"password\":\"test\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void newClientConfigurationFailsClosedOnUnsafeUrls() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> OidcUrlPolicy.validateWebUri("http://internal.example.com/callback", false));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> OidcUrlPolicy.validateWebUri("https://user@example.com/callback", false));
    }

    @Test
    void oidcOnlyManagedApplicationDoesNotRequireAUserSyncProvider() throws Exception {
        var app = applicationService.createManagedApplication(new SaveApplicationCommand("oidc-managed", "Managed Agent",
                "http://localhost:9000", CALLBACK, "http://localhost:9000/permissions",
                "", "default", CLIENT_SECRET, 0), user.getId());
        accessService.changeUserApplicationAccess(user.getId(), app.getAppCode(), "enabled", 0, user.getId(), null);

        assertThat(accessService.isAuthorized(user.getId(), app.getAppCode())).isTrue();
        var result = mvc.perform(get("/oauth2/authorize").session(session())
                .queryParam("client_id", app.getAppCode()).queryParam("redirect_uri", CALLBACK)
                .queryParam("response_type", "code").queryParam("scope", "openid profile")
                .queryParam("state", "managed-state").queryParam("nonce", NONCE)
                .queryParam("code_challenge", challenge(VERIFIER)).queryParam("code_challenge_method", "S256"))
                .andReturn();
        assertThat(code(result)).isNotBlank();
        accessService.changeUserApplicationAccess(user.getId(), app.getAppCode(), "disabled", 1, user.getId(), null);
        assertThat(policy.allows(user.getId(), app.getAppCode())).isFalse();
    }

    @Test
    void introspectionDoesNotReportRevokedUserAccessAsActive() throws Exception {
        var tokens = body(exchange(code(authorize(session(), challenge(VERIFIER), CALLBACK)), VERIFIER, CLIENT_SECRET));
        accessService.deauthorize(user.getId(), CLIENT);
        var basic = Base64.getEncoder().encodeToString((CLIENT + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        var result = mvc.perform(post("/oauth2/introspect").header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", tokens.path("access_token").asText())).andExpect(status().isOk()).andReturn();
        assertThat(body(result).path("active").asBoolean()).isFalse();
    }

    @Test
    void rpInitiatedLogoutClearsAccountSessionAndReturnsOnlyToRegisteredAddress() throws Exception {
        var session = session();
        var tokens = body(exchange(code(authorize(session, challenge(VERIFIER), CALLBACK)), VERIFIER, CLIENT_SECRET));
        var result = mvc.perform(get("/connect/logout").session(session)
                .queryParam("id_token_hint", tokens.path("id_token").asText())
                .queryParam("post_logout_redirect_uri", "http://localhost:9000/logged-out")
                .queryParam("state", "logout-state")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(302);
        assertThat(result.getResponse().getRedirectedUrl()).startsWith("http://localhost:9000/logged-out")
                .contains("state=logout-state");
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void disabledApplicationRejectsPreviouslyIssuedAccessToken() throws Exception {
        var tokens = body(exchange(code(authorize(session(), challenge(VERIFIER), CALLBACK)), VERIFIER, CLIENT_SECRET));
        applicationService.changeManagedApplicationStatus(CLIENT, "disabled", applicationService.getApplication(CLIENT).getVersion(), null);
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.path("access_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userInfoRequiresBearerEvenWhenAccountBrowserSessionExists() throws Exception {
        mvc.perform(get("/userinfo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/userinfo").session(session())).andExpect(status().isUnauthorized());
    }

    @Test
    void idTokenCannotBeUsedAsUserInfoAccessToken() throws Exception {
        var tokens = body(exchange(code(authorize(session(), challenge(VERIFIER), CALLBACK)), VERIFIER, CLIENT_SECRET));
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.path("id_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void standardTokenRevocationMakesUserInfoRejectTheToken() throws Exception {
        var tokens = body(exchange(code(authorize(session(), challenge(VERIFIER), CALLBACK)), VERIFIER, CLIENT_SECRET));
        var basic = Base64.getEncoder().encodeToString((CLIENT + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        mvc.perform(post("/oauth2/revoke").header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("token", tokens.path("access_token").asText())).andExpect(status().isOk());
        mvc.perform(get("/userinfo").header("Authorization", "Bearer " + tokens.path("access_token").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issuerMustMatchEvenForATokenSignedByTheCurrentKey() throws Exception {
        var tokens = body(exchange(code(authorize(session(), challenge(VERIFIER), CALLBACK)), VERIFIER, CLIENT_SECRET));
        var valid = applicationDecoder.decode(tokens.path("access_token").asText());
        var foreignClaims = JwtClaimsSet.builder().claims(claims -> claims.putAll(valid.getClaims()))
                .issuer("https://different-issuer.example.com").build();
        var signed = new NimbusJwtEncoder(signingKeys).encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("test-signing").build(), foreignClaims));
        Assertions.assertThrows(JwtValidationException.class, () -> applicationDecoder.decode(signed.getTokenValue()));
    }

    private MockHttpSession session() {
        var session = new MockHttpSession();
        session.setAttribute(AuthController.SESSION_USER_KEY,
                new AccountSessionUser(user.getId(), user.getAccount(), user.getName()));
        return session;
    }

    private MvcResult authorize(MockHttpSession session, String challenge, String callback) throws Exception {
        return authorize(session, challenge, callback, "test-state", NONCE);
    }

    private MvcResult authorize(MockHttpSession session, String challenge, String callback,
                                String state, String nonce) throws Exception {
        var request = get("/oauth2/authorize")
                .queryParam("client_id", CLIENT).queryParam("redirect_uri", callback).queryParam("response_type", "code")
                .queryParam("scope", "openid profile").queryParam("state", state).queryParam("nonce", nonce)
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256");
        if (session != null) request.session(session);
        return mvc.perform(request).andReturn();
    }

    private MvcResult exchange(String code, String verifier, String secret) throws Exception {
        var basic = Base64.getEncoder().encodeToString((CLIENT + ":" + secret).getBytes(StandardCharsets.UTF_8));
        return mvc.perform(post("/oauth2/token").header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "authorization_code")
                .param("code", code).param("redirect_uri", CALLBACK).param("code_verifier", verifier)).andReturn();
    }

    private String code(MvcResult result) {
        var location = result.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull().doesNotContain("error=");
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private NimbusJwtDecoder decoder() throws Exception {
        var jwks = mvc.perform(get("/oauth2/jwks")).andReturn().getResponse().getContentAsString();
        var key = (RSAKey) JWKSet.parse(jwks).getKeys().get(0);
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    private static String challenge(String verifier) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
    }

    private static String csrf(MvcResult result) throws Exception {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"")
                .matcher(result.getResponse().getContentAsString());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static Path createKeyStore() {
        try {
            Path directory = Files.createTempDirectory("account-oidc-test-");
            Path path = directory.resolve("signing.p12");
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool.exe").toString(),
                    "-genkeypair", "-alias", "test-signing", "-keyalg", "RSA", "-keysize", "2048",
                    "-dname", "CN=Account-OIDC-Test", "-validity", "2", "-storetype", "PKCS12",
                    "-keystore", path.toString(), "-storepass", KEY_PASSWORD).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            if (process.waitFor() != 0) throw new IllegalStateException("Test key generation failed");
            path.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            return path;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare an isolated test signing key", exception);
        }
    }
}
