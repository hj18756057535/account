package com.hypers.account.starter.sign;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Account Center HMAC-SHA256 签名工具。
 * 应用侧使用此工具校验来自 Account Center 的同步请求签名，
 * 以及向 Account Center OpenAPI 发送请求时生成签名。
 *
 * 签名原文格式：method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body
 */
public class AccountHmacSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /** 对签名原文进行 HMAC-SHA256 签名，返回 Base64 编码结果 */
    public String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign payload", ex);
        }
    }

    /** 验证签名是否匹配（使用常量时间比较防止时序攻击） */
    public boolean verify(String payload, String secret, String signature) {
        String expected = sign(payload, secret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /** 构造签名原文 */
    public String buildSignText(String method, String path, String timestamp, String nonce, String body) {
        return method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body;
    }
}
