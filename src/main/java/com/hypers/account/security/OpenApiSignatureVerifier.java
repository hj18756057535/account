package com.hypers.account.security;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class OpenApiSignatureVerifier {

    private static final Duration ALLOWED_SKEW = Duration.ofMinutes(5);

    private final AccountDirectoryService directoryService;
    private final HmacSignatureService signatureService;
    private final InMemoryNonceStore nonceStore;
    private final Clock clock;

    public OpenApiSignatureVerifier(AccountDirectoryService directoryService,
                                    HmacSignatureService signatureService,
                                    InMemoryNonceStore nonceStore,
                                    Clock clock) {
        this.directoryService = directoryService;
        this.signatureService = signatureService;
        this.nonceStore = nonceStore;
        this.clock = clock;
    }

    public AccountApplication verify(HttpServletRequest request, String body, String purpose) {
        String appCode = requiredHeader(request, "X-Account-App-Code");
        String timestamp = requiredHeader(request, "X-Account-Timestamp");
        String nonce = requiredHeader(request, "X-Account-Nonce");
        String signature = requiredHeader(request, "X-Account-Signature");
        Instant requestTime = parseTimestamp(timestamp);
        Instant now = clock.instant();
        if (requestTime.isBefore(now.minus(ALLOWED_SKEW)) || requestTime.isAfter(now.plus(ALLOWED_SKEW))) {
            throw unauthorized("signature timestamp expired");
        }
        if (!nonceStore.markIfAbsent(appCode, nonce, purpose, now.plus(ALLOWED_SKEW))) {
            throw unauthorized("nonce has been used");
        }
        AccountApplication application = directoryService.getApplication(appCode);
        if ("disabled".equals(application.getStatus())) {
            throw unauthorized("application is disabled");
        }
        String signText = request.getMethod()
                + "\n" + request.getRequestURI()
                + "\n" + timestamp
                + "\n" + nonce
                + "\n" + body;
        if (!signatureService.verify(signText, application.getSecret(), signature)) {
            throw unauthorized("signature mismatch");
        }
        return application;
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.trim().isEmpty()) {
            throw unauthorized("missing signature header");
        }
        return value;
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(timestamp));
        } catch (NumberFormatException e) {
            throw unauthorized("invalid signature timestamp");
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
