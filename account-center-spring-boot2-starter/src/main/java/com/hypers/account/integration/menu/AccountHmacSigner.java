package com.hypers.account.integration.menu;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AccountHmacSigner {

    public String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to sign payload", exception);
        }
    }

    public boolean verify(String payload, String secret, String signature) {
        if (signature == null) return false;
        return MessageDigest.isEqual(
                sign(payload, secret).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    public String buildSignText(String method, String path, String timestamp, String nonce, String body) {
        return method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body;
    }
}
