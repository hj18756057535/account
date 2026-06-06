package com.hypers.account.starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.hypers.account.starter.sign.AccountHmacSigner;
import org.junit.jupiter.api.Test;

class AccountHmacSignerTest {

    @Test
    void signAndVerifyWorkCorrectly() {
        AccountHmacSigner signer = new AccountHmacSigner();
        String payload = "POST\n/openapi/sso/tickets/exchange\n1710000000000\nabc123\n{\"appCode\":\"cms-ai\"}";
        String secret = "test-secret";

        String signature = signer.sign(payload, secret);

        assertThat(signer.verify(payload, secret, signature)).isTrue();
        assertThat(signer.verify(payload + "tampered", secret, signature)).isFalse();
    }

    @Test
    void buildSignTextFormatsCorrectly() {
        AccountHmacSigner signer = new AccountHmacSigner();
        String signText = signer.buildSignText("POST", "/path", "12345", "nonce", "{\"key\":\"value\"}");

        assertThat(signText).isEqualTo("POST\n/path\n12345\nnonce\n{\"key\":\"value\"}");
    }
}
