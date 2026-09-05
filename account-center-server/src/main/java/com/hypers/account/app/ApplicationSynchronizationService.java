package com.hypers.account.app;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.contract.application.AccountUserDesiredState;
import com.hypers.account.contract.application.AccountUserSyncResult;
import com.hypers.account.web.management.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationSynchronizationService {
    private static final long[] DELAYS = {5, 30, 120, 300};
    private final ApplicationSyncStore store;
    private final AccountDirectoryService directory;
    private final ApplicationSyncPolicy policy;
    private final ApplicationSyncTransport transport;
    private final ObjectMapper json;
    private final Clock clock;
    private final PlatformTransactionManager transactions;
    private final AuditLogService audit;

    // 写准入与处理回执统一按应用、准入、命令顺序加锁，网络调用不持有数据库事务。
    public void prepareChange(String appCode) {
        if (policy.databaseMode() && store.lockApplication(appCode) == null) throw notFound();
    }

    public String created(String id, String operator) {
        if (!policy.databaseMode()) return "pending_application_adaptation";
        var delivery = store.find(id);
        delivery.setOperatorId(operator);
        var app = directory.getApplication(delivery.getAppCode());
        store.supersede(delivery.getUserId(), delivery.getAppCode(), delivery.getSyncVersion(), clock.instant());
        if (policy.canDeliver(app)) enqueue(delivery, app, false);
        else { delivery.setUpdatedAt(clock.instant()); store.save(delivery); }
        return delivery.getStatus();
    }

    public void requireReuseConfirmation(String userId, String appCode, String desiredStatus, boolean confirmed) {
        if (!policy.databaseMode() || !"enabled".equals(desiredStatus) || confirmed) return;
        var access = store.lockAccess(userId, appCode);
        if (access != null && "disabled".equals(access.getDesiredStatus()) && store.state(userId, appCode) != null)
            throw new ApiException(HttpStatus.CONFLICT, "SYNC_REUSE_CONFIRMATION_REQUIRED", "sync.reuse");
    }

    public ApplicationAccess retry(String userId, String appCode, long version, String operator) {
        return tx(() -> {
            if (!policy.databaseMode()) throw unavailable("SYNC_DISABLED");
            prepareChange(appCode);
            var access = store.lockAccess(userId, appCode);
            if (access == null) throw notFound();
            if (access.getVersion() != version) throw conflict();
            var delivery = store.latest(userId, appCode, version);
            if (delivery == null) throw conflict();
            delivery = store.lock(delivery.getId());
            if (!List.of("failed", "pending_application_adaptation").contains(delivery.getStatus())
                    || delivery.getCreatedAt().plus(Duration.ofDays(7)).isBefore(clock.instant())) throw conflict();
            var app = directory.getApplication(appCode);
            try { policy.target(app); }
            catch (ApplicationSyncFailure error) { throw unavailable(error.getCode()); }
            if (delivery.getOperatorId() == null) delivery.setOperatorId(operator);
            enqueue(delivery, app, true);
            audit.log(operator, "APPLICATION_SYNC_RETRIED", "USER_APPLICATION", userId + ":" + appCode, "{}");
            access.setIntegrationStatus(delivery.getStatus());
            access.setSyncCommandId(delivery.getId());
            return access;
        });
    }

    public void decorate(ApplicationAccess access, AccountApplication app) {
        access.setRetryable(app != null && access.getSyncCommandId() != null && policy.canDeliver(app)
                && List.of("failed", "pending_application_adaptation").contains(access.getIntegrationStatus()));
        if ("succeeded".equals(access.getIntegrationStatus()) && (access.getAppliedVersion() == null
                || access.getAppliedVersion() != access.getVersion()
                || !access.getDesiredStatus().equals(access.getAppliedStatus()))) {
            access.setIntegrationStatus("failed");
            access.setLastErrorCode("SYNC_RESULT_INVALID");
            access.setRetryable(false);
        }
    }

    public void populateConfirmation(ApplicationAccess access) {
        if (!policy.databaseMode()) return;
        var state = store.state(access.getUserId(), access.getAppCode());
        if (state != null) {
            access.setAppliedStatus(state.getAppliedStatus());
            access.setAppliedVersion(state.getAppliedVersion());
            access.setLastSyncedAt(state.getLastSyncedAt());
        }
    }

    private void enqueue(ApplicationSyncDelivery delivery, AccountApplication app, boolean retry) {
        var user = directory.getUser(delivery.getUserId());
        if ("enabled".equals(delivery.getDesiredStatus()) && !"enabled".equals(user.getStatus()))
            throw unavailable("SYNC_USER_DISABLED");
        store.enroll(app.getAppCode());
        if (delivery.getPayloadJson() == null) {
            var state = store.state(delivery.getUserId(), delivery.getAppCode());
            var payload = AccountUserDesiredState.builder().appCode(delivery.getAppCode())
                    .globalUserId(delivery.getUserId()).localUserId(state == null ? null : state.getLocalUserId())
                    .account(user.getAccount()).displayName(user.getName()).email(user.getEmail()).phone(user.getPhone())
                    .tenantCode(app.getDefaultTenantCode()).desiredStatus(delivery.getDesiredStatus())
                    .syncVersion(delivery.getSyncVersion()).occurredAt(delivery.getCreatedAt()).build();
            try { delivery.setPayloadJson(json.writeValueAsString(payload)); }
            catch (JsonProcessingException error) { throw new IllegalStateException("Cannot serialize sync payload", error); }
            if (delivery.getPayloadJson().getBytes(StandardCharsets.UTF_8).length > 16 * 1024)
                throw unavailable("SYNC_PAYLOAD_TOO_LARGE");
            delivery.setPayloadHash(hash(delivery.getPayloadJson()));
        }
        // 重试是管理员对当前已配置目标的重新确认，固定请求及幂等键不改变。
        delivery.setTargetApplicationVersion(app.getVersion());
        delivery.setStatus("pending");
        if (retry) delivery.setAttempts(0);
        delivery.setErrorCode(null);
        delivery.setLeaseToken(null);
        delivery.setLeaseExpiresAt(null);
        delivery.setNextRetryAt(clock.instant());
        delivery.setUpdatedAt(clock.instant());
        store.save(delivery);
    }

    @Scheduled(fixedDelayString = "${account.application-sync.poll-ms:5000}")
    public void tick() {
        if (!policy.databaseMode()) return;
        for (String id : store.expired(clock.instant().minus(Duration.ofDays(7)))) {
            tx(() -> {
                var delivery = lockCurrent(id);
                if (delivery != null && !List.of("succeeded", "superseded", "expired").contains(delivery.getStatus())
                        && delivery.getCreatedAt().plus(Duration.ofDays(7)).isBefore(clock.instant())) {
                    terminal(delivery, "expired", "SYNC_EXPIRED");
                }
                return null;
            });
        }
        if (!policy.enabled()) return;
        for (String id : store.due(clock.instant())) {
            try { process(id); }
            catch (RuntimeException error) {
                // 不记录 payload、URL、异常消息或堆栈，租约到期后可恢复。
                log.warn("Application sync processing interrupted: command={}, category={}", id, error.getClass().getSimpleName());
            }
        }
    }

    public void process(String id) {
        if (!policy.enabled()) return;
        var claimed = tx(() -> claim(id));
        if (claimed == null) return;
        AccountUserSyncResult result;
        try {
            var app = directory.getApplication(claimed.getAppCode());
            if (!app.getVersion().equals(claimed.getTargetApplicationVersion()))
                throw new ApplicationSyncFailure("SYNC_TARGET_CHANGED", false);
            result = transport.send(claimed, app);
        } catch (ApplicationSyncFailure error) {
            tx(() -> { fail(claimed, error); return null; });
            return;
        }
        try {
            tx(() -> { confirm(claimed, result); return null; });
        } catch (DataIntegrityViolationException error) {
            tx(() -> { fail(claimed, new ApplicationSyncFailure("SYNC_IDENTITY_CONFLICT", false)); return null; });
        }
    }

    private ApplicationSyncDelivery claim(String id) {
        var delivery = lockCurrent(id);
        if (delivery == null) return null;
        Instant now = clock.instant();
        boolean leased = "processing".equals(delivery.getStatus());
        if (leased && (delivery.getLeaseExpiresAt() == null || delivery.getLeaseExpiresAt().isAfter(now))) return null;
        if (!leased && !List.of("pending", "retry_wait").contains(delivery.getStatus())) return null;
        if (!leased && delivery.getNextRetryAt() != null && delivery.getNextRetryAt().isAfter(now)) return null;
        if (delivery.getAttempts() >= 5) { terminal(delivery, "failed", "SYNC_RETRY_EXHAUSTED"); return null; }
        if (delivery.getCreatedAt().plus(Duration.ofDays(7)).isBefore(now)) {
            terminal(delivery, "expired", "SYNC_EXPIRED"); return null;
        }
        var app = directory.getApplication(delivery.getAppCode());
        try { policy.target(app); }
        catch (ApplicationSyncFailure error) { terminal(delivery, "failed", error.getCode()); return null; }
        if (!app.getVersion().equals(delivery.getTargetApplicationVersion())) {
            terminal(delivery, "failed", "SYNC_TARGET_CHANGED"); return null;
        }
        if ("enabled".equals(delivery.getDesiredStatus())
                && !"enabled".equals(directory.getUser(delivery.getUserId()).getStatus())) {
            terminal(delivery, "failed", "SYNC_USER_DISABLED"); return null;
        }
        if (delivery.getPayloadJson() == null || !hash(delivery.getPayloadJson()).equals(delivery.getPayloadHash())) {
            terminal(delivery, "failed", "SYNC_PAYLOAD_INVALID"); return null;
        }
        delivery.setStatus("processing");
        delivery.setAttempts(delivery.getAttempts() + 1);
        delivery.setLeaseToken(UUID.randomUUID().toString());
        delivery.setLeaseExpiresAt(now.plusSeconds(30));
        delivery.setUpdatedAt(now);
        store.save(delivery);
        return delivery;
    }

    private ApplicationSyncDelivery lockCurrent(String id) {
        var hint = store.find(id);
        if (hint == null || store.lockApplication(hint.getAppCode()) == null) return null;
        var access = store.lockAccess(hint.getUserId(), hint.getAppCode());
        var delivery = store.lock(id);
        if (delivery == null) return null;
        if (access == null || delivery.getSyncVersion() != access.getVersion()
                || !delivery.getDesiredStatus().equals(access.getDesiredStatus())) {
            if (!List.of("succeeded", "superseded", "expired").contains(delivery.getStatus()))
                terminal(delivery, "superseded", null);
            return null;
        }
        return delivery;
    }

    private ApplicationSyncDelivery owned(ApplicationSyncDelivery claimed) {
        var current = lockCurrent(claimed.getId());
        return current != null && "processing".equals(current.getStatus())
                && claimed.getLeaseToken().equals(current.getLeaseToken())
                && current.getLeaseExpiresAt().isAfter(clock.instant()) ? current : null;
    }

    private void confirm(ApplicationSyncDelivery claimed, AccountUserSyncResult result) {
        var current = owned(claimed);
        if (current == null) return;
        var app = directory.getApplication(current.getAppCode());
        if (!policy.canDeliver(app) || !app.getVersion().equals(current.getTargetApplicationVersion())) {
            terminal(current, "failed", "SYNC_TARGET_CHANGED"); return;
        }
        var state = store.state(current.getUserId(), current.getAppCode());
        if (state != null && !state.getLocalUserId().equals(result.getLocalUserId())) {
            terminal(current, "failed", "SYNC_IDENTITY_CONFLICT"); return;
        }
        boolean create = state == null;
        if (create) {
            state = new ApplicationUserState();
            state.setUserId(current.getUserId());
            state.setAppCode(current.getAppCode());
            state.setLocalUserId(result.getLocalUserId());
            state.setCreatedBy(current.getOperatorId());
            state.setCreatedAt(clock.instant());
        }
        state.setAppliedStatus(result.getAppliedStatus());
        state.setAppliedVersion(result.getAppliedVersion());
        state.setLastSyncedAt(clock.instant());
        state.setUpdatedBy(current.getOperatorId());
        state.setUpdatedAt(clock.instant());
        if (create) store.insertState(state); else store.updateState(state);
        terminal(current, "succeeded", null);
        audit.log(current.getOperatorId(), "APPLICATION_SYNC_CONFIRMED", "USER_APPLICATION",
                current.getUserId() + ":" + current.getAppCode(), "{\"version\":" + current.getSyncVersion() + "}");
    }

    private void fail(ApplicationSyncDelivery claimed, ApplicationSyncFailure error) {
        var current = owned(claimed);
        if (current == null) return;
        if (error.isRetryable() && current.getAttempts() < 5) {
            current.setStatus("retry_wait");
            current.setErrorCode(error.getCode());
            current.setNextRetryAt(clock.instant().plusSeconds(DELAYS[current.getAttempts() - 1]));
            current.setLeaseToken(null);
            current.setLeaseExpiresAt(null);
            current.setUpdatedAt(clock.instant());
            store.save(current);
        } else terminal(current, "failed", error.getCode());
    }

    private void terminal(ApplicationSyncDelivery delivery, String status, String error) {
        delivery.setStatus(status);
        delivery.setErrorCode(error);
        delivery.setUpdatedAt(clock.instant());
        delivery.setLeaseToken(null);
        delivery.setLeaseExpiresAt(null);
        delivery.setNextRetryAt(null);
        if (List.of("succeeded", "superseded", "expired").contains(status)) delivery.setPayloadJson(null);
        store.save(delivery);
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private <T> T tx(Supplier<T> action) {
        var template = new TransactionTemplate(transactions);
        // MySQL 默认 RR 下，抢锁前查命令会建立旧快照；后续配置/映射读取必须看到锁后的提交。
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template.execute(status -> action.get());
    }
    private ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, "RESOURCE_VERSION_CONFLICT", "sync.conflict"); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "error.resourceNotFound"); }
    private ApiException unavailable(String code) { return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, "sync.unavailable"); }
}
