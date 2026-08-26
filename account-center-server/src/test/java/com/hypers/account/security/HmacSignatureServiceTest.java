package com.hypers.account.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HmacSignatureServiceTest {

    @Test
    void verifyReturnsTrueForMatchingSignatureAndFalseForTamperedPayload() {
        HmacSignatureService service = new HmacSignatureService();
        String payload = "appCode=cms-ai&nonce=n-1&timestamp=1710000000000";
        String secret = "test-secret";

        String signature = service.sign(payload, secret);

        assertThat(service.verify(payload, secret, signature)).isTrue();
        assertThat(service.verify(payload + "&extra=value", secret, signature)).isFalse();
    }
}
