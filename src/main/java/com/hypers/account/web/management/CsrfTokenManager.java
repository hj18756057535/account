package com.hypers.account.web.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

@Component
public class CsrfTokenManager {

    public static final String HEADER_NAME = "X-CSRF-Token";
    private static final String SESSION_KEY = CsrfTokenManager.class.getName() + ".token";

    private final SecureRandom secureRandom = new SecureRandom();

    public String ensureToken(HttpSession session) {
        Object current = session.getAttribute(SESSION_KEY);
        if (current instanceof String && !((String) current).isEmpty()) {
            return (String) current;
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_KEY, token);
        return token;
    }

    public boolean matches(HttpSession session, String candidate) {
        if (session == null || candidate == null || candidate.isEmpty()) {
            return false;
        }
        Object expected = session.getAttribute(SESSION_KEY);
        if (!(expected instanceof String)) {
            return false;
        }
        return MessageDigest.isEqual(
                ((String) expected).getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
