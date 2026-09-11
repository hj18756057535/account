package com.hypers.account.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.starter.sign.AccountHmacSigner;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MenuPermissionConformanceTest {

    @Test
    void matchesSharedSignatureVector() throws Exception {
        Path vectorPath = Path.of("..", "integration-kits", "conformance", "menu_permission_v1.json");
        assertThat(vectorPath).exists();
        JsonNode vector = new ObjectMapper().readTree(Files.readString(vectorPath));
        AccountHmacSigner signer = new AccountHmacSigner();
        String signText = signer.buildSignText(
                vector.get("method").asText(),
                vector.get("path").asText(),
                vector.get("timestamp").asText(),
                vector.get("nonce").asText(),
                vector.get("body").asText());

        assertThat(signer.sign(signText, vector.get("secret").asText()))
                .isEqualTo(vector.get("signature").asText());
    }
}
