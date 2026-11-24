package com.hypers.account.app.directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.ApplicationSyncFailure;
import com.hypers.account.app.ApplicationSyncPolicy;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.contract.AccountProviderProtocol;
import com.hypers.account.contract.directory.AccountDirectoryPage;
import com.hypers.account.contract.directory.AccountDirectoryQuery;
import com.hypers.account.security.HmacSignatureService;
import com.hypers.account.web.management.ApiException;
import java.net.URI;
import java.math.BigInteger;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 仅调用已登记的应用地址，限制超时、分页和响应大小，不跟随重定向。 */
@Component
@RequiredArgsConstructor
public class ApplicationDirectoryClient {
    public static final int MAX_ITEMS = 1000;
    private final ApplicationSyncPolicy policy;
    private final ObjectMapper json;
    private final HmacSignatureService signatures;
    private final Clock clock;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public void check(AccountApplication app) {
        page(app, "users", null, 1);
    }

    public ApplicationDirectorySnapshot fetch(AccountApplication app) {
        ApplicationDirectorySnapshot snapshot = new ApplicationDirectorySnapshot();
        snapshot.setAppCode(app.getAppCode());
        for (String kind : List.of("users", "roles", "organizations")) {
            String cursor = null;
            int total = 0;
            do {
                AccountDirectoryPage page = page(app, kind, cursor, 200);
                int count = "users".equals(kind) ? page.getUsers().size()
                        : "roles".equals(kind) ? page.getRoles().size() : page.getOrganizations().size();
                total += count;
                if (total > MAX_ITEMS) {
                    throw failure("DIRECTORY_TOO_LARGE", "directory.tooLarge");
                }
                snapshot.getUsers().addAll(page.getUsers());
                snapshot.getRoles().addAll(page.getRoles());
                snapshot.getOrganizations().addAll(page.getOrganizations());
                String next = page.getNextCursor();
                if (next != null && (count == 0 || !next.matches("[1-9][0-9]{0,18}")
                        || cursor != null && new BigInteger(next).compareTo(new BigInteger(cursor)) <= 0)) {
                    throw invalid();
                }
                cursor = next;
            } while (cursor != null);
        }
        snapshot.setCapturedAt(clock.instant());
        return snapshot;
    }

    private AccountDirectoryPage page(AccountApplication app, String kind, String cursor, int limit) {
        URI base;
        try {
            base = policy.target(app);
        } catch (ApplicationSyncFailure | IllegalArgumentException error) {
            throw failure("DIRECTORY_NOT_CONFIGURED", "directory.notConfigured");
        }
        AccountDirectoryQuery query = new AccountDirectoryQuery();
        query.setKind(kind);
        query.setAfterId(cursor);
        query.setLimit(limit);
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            String body = json.writeValueAsString(query);
            String timestamp = String.valueOf(clock.millis());
            String nonce = UUID.randomUUID().toString();
            String path = AccountProviderProtocol.DIRECTORY_PATH;
            String signature = signatures.sign("POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body, app.getSecret());
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header(AccountIntegrationHeaders.APP_CODE, app.getAppCode())
                    .header(AccountIntegrationHeaders.PROTOCOL_VERSION, AccountProviderProtocol.VERSION)
                    .header(AccountIntegrationHeaders.TIMESTAMP, timestamp)
                    .header(AccountIntegrationHeaders.NONCE, nonce)
                    .header(AccountIntegrationHeaders.SIGNATURE, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            pending = client.sendAsync(request, ignored -> new LimitedBody());
            HttpResponse<byte[]> response = pending.get(6, TimeUnit.SECONDS);
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw failure("DIRECTORY_AUTH_FAILED", "directory.authFailed");
            }
            if (response.statusCode() == 404 || response.statusCode() == 426) {
                throw failure("DIRECTORY_PROVIDER_MISSING", "directory.providerMissing");
            }
            if (response.statusCode() != 200) {
                throw failure("DIRECTORY_UNAVAILABLE", "directory.unavailable");
            }
            JsonNode tree = json.readTree(response.body());
            if (tree == null || !app.getAppCode().equals(tree.path("appCode").asText())
                    || !kind.equals(tree.path("kind").asText())
                    || !(app.getDefaultTenantCode() == null || app.getDefaultTenantCode().isBlank()
                    ? "default" : app.getDefaultTenantCode())
                    .equals(tree.path("tenantCode").asText())) {
                throw invalid();
            }
            String arrayName = kind;
            JsonNode rows = tree.get(arrayName);
            String id = "users".equals(kind) ? "localUserId" : "roles".equals(kind) ? "localRoleId" : "localOrganizationId";
            if (rows == null || !rows.isArray() || rows.size() > limit) {
                throw invalid();
            }
            for (JsonNode row : rows) {
                if (!row.path(id).isTextual() || !row.path(id).asText().matches("[1-9][0-9]{0,18}")) {
                    throw invalid();
                }
                if (!"enabled".equals(row.path("status").asText()) && !"disabled".equals(row.path("status").asText())) {
                    throw invalid();
                }
                if ("users".equals(kind)) {
                    validateReferences(row.get("roleIds"));
                    validateReferences(row.get("organizationIds"));
                }
                JsonNode parent = row.get("parentId");
                if ("organizations".equals(kind) && parent != null && !parent.isNull()
                        && (!parent.isTextual() || !parent.asText().matches("[1-9][0-9]{0,18}"))) {
                    throw invalid();
                }
            }
            AccountDirectoryPage result = json.treeToValue(tree, AccountDirectoryPage.class);
            if (!"users".equals(kind) && !result.getUsers().isEmpty()
                    || !"roles".equals(kind) && !result.getRoles().isEmpty()
                    || !"organizations".equals(kind) && !result.getOrganizations().isEmpty()) {
                throw invalid();
            }
            return result;
        } catch (ApiException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw failure("DIRECTORY_UNAVAILABLE", "directory.unavailable");
        } catch (Exception error) {
            throw failure("DIRECTORY_UNAVAILABLE", "directory.unavailable");
        } finally {
            if (pending != null && !pending.isDone()) {
                pending.cancel(true);
            }
        }
    }

    private ApiException invalid() {
        return failure("DIRECTORY_RESULT_INVALID", "directory.invalid");
    }

    private void validateReferences(JsonNode values) {
        if (values == null || !values.isArray() || values.size() > MAX_ITEMS) {
            throw invalid();
        }
        for (JsonNode value : values) {
            if (!value.isTextual() || !value.asText().matches("[1-9][0-9]{0,18}")) {
                throw invalid();
            }
        }
    }

    private ApiException failure(String code, String key) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, key);
    }

    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int received;
        private boolean terminated;

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (terminated) {
                return;
            }
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > 2 * 1024 * 1024 - received) {
                    terminated = true;
                    subscription.cancel();
                    delegate.onError(new IllegalStateException("Directory response exceeds limit"));
                    return;
                }
                received += buffer.remaining();
            }
            delegate.onNext(buffers);
        }
        @Override
        public void onError(Throwable error) {
            if (!terminated) {
                delegate.onError(error);
            }
        }

        @Override
        public void onComplete() {
            if (!terminated) {
                delegate.onComplete();
            }
        }
    }
}
