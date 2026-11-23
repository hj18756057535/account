package com.hypers.account.oidc;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OidcUrlPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"https://10.20.30.40:8088", "https://192.168.1.20", "https://account.example.com",
            "http://localhost:8088", "http://127.0.0.1:8088", "http://[::1]:8088"})
    void fixedIpHttpsAndLoopbackHttpNeedNoIntranetOverride(String url) {
        assertDoesNotThrow(() -> OidcUrlPolicy.validateIssuer(url, false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://10.20.30.40:8088", "http://172.16.0.20:8088", "http://172.31.255.20:8088",
            "http://192.168.1.20:8088", "http://[fd12:3456::20]:8088"})
    void privateIpHttpRequiresExplicitEnvironmentOverride(String url) {
        assertThrows(IllegalArgumentException.class, () -> OidcUrlPolicy.validateIssuer(url, false));
        assertDoesNotThrow(() -> OidcUrlPolicy.validateIssuer(url, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://8.8.8.8:8088", "http://172.32.0.1:8088", "http://169.254.169.254",
            "http://0.0.0.0:8088", "http://account.example.com", "http://[2001:db8::1]:8088",
            "http://10.0.0.001:8088", "https://user@example.com", "https://example.com/#fragment",
            "https://example.com:65536", "https://example.com:0", "https://example.com/*"})
    void overrideNeverAcceptsPublicHttpAmbiguousAddressesOrUnsafeUrls(String url) {
        assertThrows(IllegalArgumentException.class, () -> OidcUrlPolicy.validateWebUri(url, true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://10.20.30.40?user=someone", "http://localhost:8088?x=1"})
    void issuerCannotHaveQueryParameters(String url) {
        assertThrows(IllegalArgumentException.class, () -> OidcUrlPolicy.validateIssuer(url, true));
    }
}
