package com.hypers.account.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.starter.properties.AccountIntegrationProperties;
import com.hypers.account.starter.security.AccountIntegrationSignatureFilter;
import com.hypers.account.starter.security.AccountNonceStore;
import com.hypers.account.starter.sign.AccountHmacSigner;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AccountIntegrationInternationalizationTest {
    @Test
    void errorBoundaryKeepsCodeAndNeverReflectsInternalMessages() {
        var handler = new AccountIntegrationExceptionHandler();
        var request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "fr-FR,en-GB;q=0.8,zh;q=0.5");
        var result = handler.handle(new AccountIntegrationException(HttpStatus.CONFLICT,
                "RESOURCE_VERSION_CONFLICT", "private-error-marker"), request);
        assertThat(result.getStatusCode().value()).isEqualTo(409);
        assertThat(result.getHeaders().getFirst("Content-Language")).isEqualTo("en-US");
        assertThat(result.getBody().getCode()).isEqualTo("RESOURCE_VERSION_CONFLICT");
        assertThat(result.getBody().getMessage()).isEqualTo("The synchronization version is outdated.");
        assertThat(handler.handle(new AccountIntegrationException(HttpStatus.BAD_GATEWAY,
                "CUSTOM_ERROR", "private-error-marker"), request).getBody().getMessage())
                .isEqualTo("The integration request failed. Please retry later.");
        assertThat(handler.handleMalformedRequest(request).getBody().getMessage())
                .isEqualTo("The request body could not be parsed.");
        request.removeHeader("Accept-Language");
        request.addHeader("Accept-Language", "invalid;header");
        assertThat(handler.handleMalformedRequest(request).getBody().getMessage()).isEqualTo("请求体无法解析");
    }

    @Test
    void filterLocalizesAuthenticationFailureWithoutChangingStatus() throws Exception {
        Clock clock = Clock.systemUTC();
        var filter = new AccountIntegrationSignatureFilter(new AccountIntegrationProperties(), new AccountHmacSigner(),
                new AccountNonceStore(clock), new ObjectMapper(), clock);
        var request = new MockHttpServletRequest("PUT", "/account-integration/users/test");
        request.addHeader("Accept-Language", "en");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("Content-Language")).isEqualTo("en-US");
        assertThat(response.getContentAsString()).contains("Integration request authentication failed.");
    }

    @Test
    void catalogsHaveMatchingKeysAndNonBlankTranslations() throws Exception {
        var chinese = catalog("messages.properties");
        var english = catalog("messages_en.properties");
        assertThat(chinese.keySet()).isEqualTo(english.keySet()).hasSize(6);
        assertThat(english.values()).allMatch(value -> !value.toString().isBlank());
    }

    private Properties catalog(String name) throws Exception {
        var properties = new Properties();
        try (var stream = getClass().getClassLoader().getResourceAsStream("com/hypers/account/starter/i18n/" + name)) {
            assertThat(stream).isNotNull();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return properties;
    }
}
