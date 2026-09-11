package com.hypers.account.app.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.ApplicationMenuPermissionClient;
import com.hypers.account.app.ApplicationMenuPermissionFailure;
import com.hypers.account.app.ApplicationSyncFailure;
import com.hypers.account.app.ApplicationSyncPolicy;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.contract.AccountIntegrationPaths;
import com.hypers.account.contract.MenuPermissionProtocol;
import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import com.hypers.account.security.HmacSignatureService;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HttpApplicationMenuPermissionClient implements ApplicationMenuPermissionClient {

    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Map<String, Integer> PROVIDER_CODES = Map.of(
            "PROTOCOL_VERSION_UNSUPPORTED", 426,
            "PAYLOAD_TOO_LARGE", 413,
            "SUBJECT_NOT_FOUND", 404,
            "PERMISSION_REVISION_CONFLICT", 409,
            "IDEMPOTENCY_CONFLICT", 409,
            "SUBJECT_UNMANAGEABLE", 422,
            "PERMISSION_CODE_INVALID", 422,
            "CATALOG_INVALID", 422,
            "PERMISSION_PROVIDER_UNAVAILABLE", 503);

    private final ApplicationSyncPolicy policy;
    private final ObjectMapper json;
    private final HmacSignatureService signatures;
    private final Clock clock;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public MenuPermissionSnapshot query(AccountApplication application, MenuPermissionQuery query) {
        return send(application, "POST", AccountIntegrationPaths.MENU_PERMISSION_QUERY,
                serialize(query), null, Duration.ofSeconds(5));
    }

    @Override
    public MenuPermissionSnapshot replace(AccountApplication application,
                                          String idempotencyKey,
                                          MenuPermissionReplaceCommand command) {
        return send(application, "PUT", AccountIntegrationPaths.MENU_PERMISSION_REPLACE,
                serialize(command), idempotencyKey, Duration.ofSeconds(10));
    }

    private MenuPermissionSnapshot send(AccountApplication application,
                                        String method,
                                        String path,
                                        String body,
                                        String idempotencyKey,
                                        Duration timeout) {
        URI base;
        try {
            base = policy.target(application, MenuPermissionProtocol.VERSION);
        } catch (ApplicationSyncFailure | IllegalArgumentException exception) {
            throw unavailable();
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                HttpResponse<InputStream> response = client.send(
                        request(application, base, method, path, body, idempotencyKey, timeout),
                        HttpResponse.BodyHandlers.ofInputStream());
                byte[] responseBody = readLimited(response.body());
                if (retryable(response.statusCode()) && attempt == 0) continue;
                return decode(response, responseBody);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw unavailable();
            } catch (IOException exception) {
                if (attempt == 1) throw unavailable();
            }
        }
        throw unavailable();
    }

    private HttpRequest request(AccountApplication application,
                                URI base,
                                String method,
                                String path,
                                String body,
                                String idempotencyKey,
                                Duration timeout) {
        String timestamp = Long.toString(clock.millis());
        String nonce = UUID.randomUUID().toString();
        String signature = signatures.sign(method + '\n' + path + '\n' + timestamp + '\n' + nonce + '\n' + body,
                application.getSecret());
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header(AccountIntegrationHeaders.APP_CODE, application.getAppCode())
                .header(AccountIntegrationHeaders.PROTOCOL_VERSION, MenuPermissionProtocol.VERSION)
                .header(AccountIntegrationHeaders.TIMESTAMP, timestamp)
                .header(AccountIntegrationHeaders.NONCE, nonce)
                .header(AccountIntegrationHeaders.SIGNATURE, signature)
                .method(method, HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) builder.header(AccountIntegrationHeaders.IDEMPOTENCY_KEY, idempotencyKey);
        return builder.build();
    }

    private MenuPermissionSnapshot decode(HttpResponse<InputStream> response, byte[] body) {
        if (!jsonContentType(response)) throw unavailable();
        if (response.statusCode() != 200) throw providerFailure(response.statusCode(), body);
        try {
            JsonNode tree = json.readTree(body);
            if (tree == null || !tree.isObject()) throw unavailable();
            MenuPermissionSnapshotValidator.validateJson(tree);
            return MenuPermissionSnapshotValidator.normalize(json.treeToValue(tree, MenuPermissionSnapshot.class));
        } catch (ApplicationMenuPermissionFailure exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private ApplicationMenuPermissionFailure providerFailure(int status, byte[] body) {
        try {
            JsonNode error = json.readTree(body);
            String code = error != null && error.hasNonNull("code") && error.get("code").isTextual()
                    ? error.get("code").asText() : null;
            if (Integer.valueOf(status).equals(PROVIDER_CODES.get(code))) {
                return new ApplicationMenuPermissionFailure(status, code);
            }
        } catch (Exception ignored) {
            // Invalid dependency responses are exposed only as a stable availability error.
        }
        return unavailable();
    }

    private byte[] readLimited(InputStream input) throws IOException {
        try (input) {
            byte[] body = input.readNBytes(MAX_RESPONSE_BYTES + 1);
            if (body.length > MAX_RESPONSE_BYTES) throw unavailable();
            return body;
        }
    }

    private boolean jsonContentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type")
                .map(value -> value.toLowerCase(Locale.ROOT).startsWith("application/json"))
                .orElse(false);
    }

    private boolean retryable(int status) {
        return status == 429 || status >= 500;
    }

    private String serialize(Object request) {
        try {
            return json.writeValueAsString(request);
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private ApplicationMenuPermissionFailure unavailable() {
        return new ApplicationMenuPermissionFailure(503, "PERMISSION_PROVIDER_UNAVAILABLE");
    }
}
