package com.hypers.account.app.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.ApplicationSyncDelivery;
import com.hypers.account.app.ApplicationSyncFailure;
import com.hypers.account.app.ApplicationSyncPolicy;
import com.hypers.account.app.ApplicationSyncTransport;
import com.hypers.account.contract.AccountIntegrationHeaders;
import com.hypers.account.contract.AccountIntegrationPaths;
import com.hypers.account.contract.application.AccountUserSyncResult;
import com.hypers.account.security.HmacSignatureService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ManagedApplicationSyncTransport implements ApplicationSyncTransport {
    private static final int MAX_RESPONSE = 64 * 1024;
    private final ApplicationSyncPolicy policy;
    private final ObjectMapper json;
    private final HmacSignatureService signatures;
    private final Clock clock;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public AccountUserSyncResult send(ApplicationSyncDelivery delivery, AccountApplication application) {
        URI base = policy.target(application);
        String path = AccountIntegrationPaths.applicationUser(
                URLEncoder.encode(delivery.getUserId(), StandardCharsets.UTF_8).replace("+", "%20"));
        String timestamp = Long.toString(clock.millis());
        String nonce = UUID.randomUUID().toString();
        String signature = signatures.sign("PUT\n" + path + "\n" + timestamp + "\n" + nonce + "\n"
                + delivery.getPayloadJson(), application.getSecret());
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header(AccountIntegrationHeaders.APP_CODE, delivery.getAppCode())
                .header(AccountIntegrationHeaders.IDEMPOTENCY_KEY, delivery.getIdempotencyKey())
                .header(AccountIntegrationHeaders.TIMESTAMP, timestamp)
                .header(AccountIntegrationHeaders.NONCE, nonce)
                .header(AccountIntegrationHeaders.SIGNATURE, signature)
                .PUT(HttpRequest.BodyPublishers.ofString(delivery.getPayloadJson())).build();
        CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(request, ignored -> new LimitedBody());
        try {
            var response = future.get(5, TimeUnit.SECONDS);
            int status = response.statusCode();
            if (status != 200) throw new ApplicationSyncFailure(
                    status == 429 || status >= 500 ? "SYNC_DEPENDENCY_UNAVAILABLE" : "SYNC_RESPONSE_REJECTED",
                    status == 429 || status >= 500);
            AccountUserSyncResult result;
            try {
                var body = json.readTree(response.body());
                var version = body == null ? null : body.get("appliedVersion");
                if (version == null || !version.isIntegralNumber() || !version.canConvertToLong())
                    throw new ApplicationSyncFailure("SYNC_RESULT_INVALID", false);
                for (String field : List.of("appCode", "globalUserId", "localUserId", "appliedStatus", "resultCode")) {
                    if (body.get(field) == null || !body.get(field).isTextual())
                        throw new ApplicationSyncFailure("SYNC_RESULT_INVALID", false);
                }
                result = json.treeToValue(body, AccountUserSyncResult.class);
            }
            catch (Exception error) { throw new ApplicationSyncFailure("SYNC_RESULT_INVALID", false); }
            if (result == null || !delivery.getAppCode().equals(result.getAppCode())
                    || !delivery.getUserId().equals(result.getGlobalUserId())
                    || !delivery.getDesiredStatus().equals(result.getAppliedStatus())
                    || delivery.getSyncVersion() != result.getAppliedVersion()
                    || result.getLocalUserId() == null || result.getLocalUserId().isBlank()
                    || result.getLocalUserId().length() > 255
                    || !("APPLIED".equals(result.getResultCode()) || "ALREADY_APPLIED".equals(result.getResultCode()))) {
                throw new ApplicationSyncFailure("SYNC_RESULT_INVALID", false);
            }
            return result;
        } catch (ApplicationSyncFailure error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ApplicationSyncFailure("SYNC_DEPENDENCY_UNAVAILABLE", true);
        } catch (Exception error) {
            Throwable cause = error;
            while (cause != null) {
                if (cause instanceof ApplicationSyncFailure failure) throw failure;
                cause = cause.getCause();
            }
            throw new ApplicationSyncFailure("SYNC_DEPENDENCY_UNAVAILABLE", true);
        } finally {
            if (!future.isDone()) future.cancel(true);
        }
    }

    private static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int received;
        private boolean terminated;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (terminated) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE - received) {
                    terminated = true;
                    subscription.cancel();
                    delegate.onError(new ApplicationSyncFailure("SYNC_RESPONSE_TOO_LARGE", false));
                    return;
                }
                received += buffer.remaining();
            }
            delegate.onNext(buffers);
        }
        @Override public void onError(Throwable error) { if (!terminated) { terminated = true; delegate.onError(error); } }
        @Override public void onComplete() { if (!terminated) { terminated = true; delegate.onComplete(); } }
    }
}
