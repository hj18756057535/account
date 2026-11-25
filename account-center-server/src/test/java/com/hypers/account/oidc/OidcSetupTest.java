package com.hypers.account.oidc;

import com.hypers.account.web.management.ManagementApplicationController;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.app.service.ApplicationManagementService;
import com.hypers.account.web.management.ManagementApplicationController.CreateApplicationRequest;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class OidcSetupTest {
    @TempDir Path temporary;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void defaultsAreReusableAndChangingAddressesPreservesAllCredentials() throws Exception {
        Path directory = temporary.resolve("materials");
        OidcSetup.prepare(directory, "", "", "");
        var original = json.readTree(directory.resolve("agent-client.json").toFile());
        var firstProperties = properties(directory);
        byte[] signingKey = Files.readAllBytes(directory.resolve("signing.p12"));
        assertThat(original.path("issuer").asText()).isEqualTo("http://localhost:8088");
        assertThat(original.path("redirect_uri").asText()).isEqualTo("http://localhost:9000/auth/oidc/callback");
        assertThat(firstProperties.getProperty("account.oidc.allow-intranet-http")).isEqualTo("false");
        assertThat(firstProperties.getProperty("server.port")).isEqualTo("8088");
        OidcSetup.prepare(directory, "", "", "");
        assertThat(MessageDigest.isEqual(signingKey, Files.readAllBytes(directory.resolve("signing.p12")))).isTrue();

        OidcSetup.prepare(directory, "http://10.20.30.40:8090", "http://192.168.10.20:9001", "");
        var changed = json.readTree(directory.resolve("agent-client.json").toFile());
        var server = properties(directory);
        assertThat(changed.path("issuer").asText()).isEqualTo("http://10.20.30.40:8090");
        assertThat(changed.path("redirect_uri").asText()).isEqualTo("http://192.168.10.20:9001/auth/oidc/callback");
        assertThat(server.getProperty("account.oidc.allow-intranet-http")).isEqualTo("true");
        assertThat(server.getProperty("server.servlet.session.cookie.secure")).isEqualTo("false");
        assertThat(server.getProperty("server.port")).isEqualTo("8090");
        assertThat(changed.path("client_secret").asText().equals(original.path("client_secret").asText())).isTrue();
        assertThat(server.getProperty("account.oidc.key-store-password")
                .equals(firstProperties.getProperty("account.oidc.key-store-password"))).isTrue();
        assertThat(MessageDigest.isEqual(signingKey, Files.readAllBytes(directory.resolve("signing.p12")))).isTrue();
        assertThat(server.stringPropertyNames()).noneMatch(key -> key.endsWith("secret-hash"));

        var source = new MapConfigurationPropertySource();
        server.forEach((key, value) -> source.put((String) key, value));
        var bound = new Binder(source).bind("account.oidc", Bindable.of(OidcProperties.class)).get();
        var applicationService = mock(ApplicationManagementService.class);
        var app = new AccountApplication();
        app.setAppCode("agent");
        app.setName("Agent");
        app.setStatus("enabled");
        app.setSecretState("active");
        app.setSecret(changed.path("client_secret").asText());
        app.setNotifyBaseUrl("");
        app.setSsoCallbackUrl(changed.path("redirect_uri").asText());
        when(applicationService.getApplication("agent")).thenReturn(app);
        var repository = new OidcConfiguration().oidcClients(applicationService, bound);
        assertThat(repository.findByClientId("agent").getRedirectUris()).containsExactly(app.getSsoCallbackUrl());

        var registration = json.readValue(directory.resolve("application-registration.json").toFile(), CreateApplicationRequest.class);
        try (var validator = Validation.buildDefaultValidatorFactory()) {
            assertThat(validator.getValidator().validate(registration)).isEmpty();
        }
        assertThat(registration.getSsoCallbackUrl()).isEqualTo(app.getSsoCallbackUrl());

        OidcSetup.prepare(directory, "https://10.20.30.40", "https://192.168.10.20", "");
        assertThat(properties(directory).getProperty("account.oidc.allow-intranet-http")).isEqualTo("false");
        assertThat(properties(directory).getProperty("server.servlet.session.cookie.secure")).isEqualTo("true");
        assertThat(MessageDigest.isEqual(signingKey, Files.readAllBytes(directory.resolve("signing.p12")))).isTrue();
        assertThrows(IllegalArgumentException.class, () -> OidcSetup.prepare(directory, "", "", "another-client"));
        assertThat(json.readTree(directory.resolve("agent-client.json").toFile()).path("client_id").asText()).isEqualTo("agent");
    }

    @Test
    void unrelatedFilesAndPartialKeysArePreservedInsteadOfOverwritten() throws Exception {
        Path unrelated = Files.createDirectory(temporary.resolve("unrelated"));
        Files.writeString(unrelated.resolve("notes.txt"), "keep", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> OidcSetup.prepare(unrelated, "", "", ""));
        assertThat(Files.readString(unrelated.resolve("notes.txt"))).isEqualTo("keep");
        Path partial = Files.createDirectory(temporary.resolve("partial"));
        Files.writeString(partial.resolve("signing.p12"), "keep-partial", StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> OidcSetup.prepare(partial, "", "", ""));
        assertThat(Files.readString(partial.resolve("signing.p12"))).isEqualTo("keep-partial");
    }

    @Test
    void invalidPublicHttpCannotGenerateUsableMaterials() {
        assertThrows(IllegalArgumentException.class,
                () -> OidcSetup.prepare(temporary.resolve("invalid"), "http://8.8.8.8:8088", "", ""));
        assertThat(Files.exists(temporary.resolve("invalid/signing.p12"))).isFalse();
        assertThrows(IllegalArgumentException.class,
                () -> OidcSetup.prepare(temporary.resolve("long"), "", "http://localhost/" + "x".repeat(512), ""));
        assertThat(Files.exists(temporary.resolve("long/signing.p12"))).isFalse();
    }

    private Properties properties(Path directory) throws Exception {
        var values = new Properties();
        try (var reader = Files.newBufferedReader(directory.resolve("account-oidc.properties"), StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        return values;
    }
}
