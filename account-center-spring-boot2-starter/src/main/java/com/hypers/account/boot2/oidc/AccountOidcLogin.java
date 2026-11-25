package com.hypers.account.boot2.oidc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.WebUtils;

public final class AccountOidcLogin {

    public static final String START_PATH = "/login/account";
    public static final String CALLBACK_PATH = START_PATH + "/callback";
    public static final String EXCHANGE_PATH = START_PATH + "/exchange";

    private static final Duration REQUEST_TTL = Duration.ofMinutes(5);
    private static final Duration RESULT_TTL = Duration.ofMinutes(1);
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9_.-]{1,64}");
    private static final Pattern COOKIE_VALUE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern STATE = Pattern.compile("[A-Za-z0-9_-]{32,128}={0,2}");
    private static final Base64StringKeyGenerator BROWSER_CODES =
            new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32);
    private static final DefaultRedisScript<String> TAKE = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if value then redis.call('DEL', KEYS[1]); end; return value",
            String.class);

    private final AccountOidcClient client;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final URI loginPage;
    private final String issuer;
    private final String expectedOrigin;
    private final String keyPrefix;
    private final boolean secure;
    private final String requestCookie;
    private final String resultCookie;

    AccountOidcLogin(AccountOidcProperties properties, StringRedisTemplate redis, ObjectMapper mapper) {
        URI issuerUri = requireHttpUrl(properties.getIssuer());
        issuer = issuerUri.toString();
        URI redirect = requireHttpUrl(properties.getRedirectUri());
        loginPage = requireHttpUrl(properties.getLoginPage());
        Assert.isTrue(issuerUri.getRawFragment() == null && redirect.getRawFragment() == null
                && redirect.getPath().endsWith(CALLBACK_PATH), "Invalid Account OIDC redirect URL or issuer");
        expectedOrigin = origin(loginPage);
        Assert.isTrue(expectedOrigin.equals(origin(redirect)), "Account OIDC login page and callback must be same-origin");
        String clientId = properties.getClientId();
        Assert.isTrue(clientId != null && CLIENT_ID.matcher(clientId).matches(), "Invalid Account OIDC client ID");
        Assert.hasText(properties.getClientSecret(), "Account OIDC client secret is required");
        secure = "https".equalsIgnoreCase(redirect.getScheme());
        String cookiePrefix = (secure ? "__Host-" : "") + "AccountOidc-" + clientId;
        requestCookie = cookiePrefix + "-request";
        resultCookie = cookiePrefix + "-result";
        keyPrefix = "account:oidc:" + clientId + ":" + issuer + ":" + redirect + ":";
        this.redis = redis;
        this.mapper = mapper;
        this.client = new AccountOidcClient(properties);
    }

    URI begin(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest authorization = client.authorizationRequest(request);
        String browser = BROWSER_CODES.generateKey();
        AccountOidcClient.AuthorizationProof proof = new AccountOidcClient.AuthorizationProof(
                authorization.getAttribute(OidcParameterNames.NONCE),
                authorization.getAttribute(PkceParameterNames.CODE_VERIFIER));
        redis.opsForValue().set(requestKey(browser, authorization.getState()), serialize(proof), REQUEST_TTL);
        setCookie(response, requestCookie, browser, REQUEST_TTL);
        setCookie(response, resultCookie, "", Duration.ZERO);
        return URI.create(authorization.getAuthorizationRequestUri());
    }

    boolean complete(HttpServletRequest request, HttpServletResponse response) {
        String browser = readCookie(request, requestCookie);
        String state = singleParameter(request, "state");
        if (browser == null || state == null || !STATE.matcher(state).matches()) {
            return false;
        }
        String serialized = take(requestKey(browser, state));
        if (serialized == null) {
            return false;
        }
        setCookie(response, requestCookie, "", Duration.ZERO);
        String code = singleParameter(request, "code");
        if (request.getParameterMap().containsKey("error") || !StringUtils.hasText(code) || code.length() > 2048) {
            return false;
        }
        AccountOidcIdentity identity = client.authenticate(deserialize(serialized), state, code);
        String result = BROWSER_CODES.generateKey();
        redis.opsForValue().set(keyPrefix + "result:" + result, serialize(identity), RESULT_TTL);
        setCookie(response, resultCookie, result, RESULT_TTL);
        return true;
    }

    /** 原子领取经验证的用户身份，用于宿主首次登录建档及后续账号关联。 */
    public AccountOidcIdentity consumeIdentity(HttpServletRequest request, HttpServletResponse response) {
        if (!expectedOrigin.equals(request.getHeader(HttpHeaders.ORIGIN))) {
            return null;
        }
        String result = readCookie(request, resultCookie);
        if (result == null) {
            return null;
        }
        setCookie(response, resultCookie, "", Duration.ZERO);
        String stored = take(keyPrefix + "result:" + result);
        if (stored == null) {
            return null;
        }
        try {
            AccountOidcIdentity identity = mapper.readValue(stored, AccountOidcIdentity.class);
            Assert.isTrue(issuer.equals(identity.getIssuer()), "Unexpected Account OIDC result issuer");
            Assert.hasText(identity.getSubject(), "Account OIDC result subject is required");
            return identity;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read Account OIDC result", exception);
        }
    }

    URI loginPage(boolean success) {
        return UriComponentsBuilder.fromUri(loginPage).queryParam("accountSso", success ? "success" : "failed")
                .build(true).toUri();
    }

    private String take(String key) {
        return redis.execute(TAKE, Collections.singletonList(key));
    }

    private String requestKey(String browser, String state) {
        return keyPrefix + "request:" + browser + ":" + state;
    }

    private void setCookie(HttpServletResponse response, String name, String value, Duration ttl) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).path("/")
                .httpOnly(true).secure(secure).sameSite("Lax").maxAge(ttl).build().toString());
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie cookie = WebUtils.getCookie(request, name);
        return cookie != null && COOKIE_VALUE.matcher(cookie.getValue()).matches() ? cookie.getValue() : null;
    }

    private static String singleParameter(HttpServletRequest request, String name) {
        String[] values = request.getParameterValues(name);
        return values != null && values.length == 1 ? values[0] : null;
    }

    private String serialize(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot save Account OIDC request", exception);
        }
    }

    private AccountOidcClient.AuthorizationProof deserialize(String serialized) {
        try {
            return mapper.readValue(serialized, AccountOidcClient.AuthorizationProof.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read Account OIDC request", exception);
        }
    }

    private static URI requireHttpUrl(URI url) {
        Assert.isTrue(url != null && url.getHost() != null && url.getRawUserInfo() == null
                && url.getRawQuery() == null, "Account OIDC requires an absolute URL without user info or query");
        String host = url.getHost().toLowerCase(Locale.ROOT);
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);
        Assert.isTrue("https".equalsIgnoreCase(url.getScheme())
                        || (loopback && "http".equalsIgnoreCase(url.getScheme())),
                "Account OIDC requires HTTPS outside loopback");
        return url;
    }

    private static String origin(URI url) {
        String scheme = url.getScheme().toLowerCase(Locale.ROOT);
        int defaultPort = "https".equals(scheme) ? 443 : 80;
        int port = url.getPort();
        return scheme + "://" + url.getHost().toLowerCase(Locale.ROOT)
                + (port == -1 || port == defaultPort ? "" : ":" + port);
    }
}
