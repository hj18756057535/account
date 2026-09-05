package com.hypers.account.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.config.ApplicationSyncProperties;
import com.hypers.account.app.http.ManagedApplicationSyncTransport;
import com.hypers.account.contract.application.AccountUserSyncResult;
import com.hypers.account.security.HmacSignatureService;
import com.hypers.account.sso.AccountUserSnapshot;
import com.hypers.account.sso.SsoTicketService;
import com.hypers.account.sso.SsoTicketException;
import com.hypers.account.web.SsoTicketController;
import com.sun.net.httpserver.HttpServer;
import com.hypers.account.mapper.AdminRoleMapper;
import com.hypers.account.web.AuthController;
import com.hypers.account.web.management.ApiException;
import com.hypers.account.web.management.CsrfTokenManager;
import com.hypers.account.web.management.ManagementApplicationWriteService;
import java.net.URI;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application_sync_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "account.application-sync.poll-ms=3600000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationSynchronizationIntegrationTest {
    @Autowired private AccountDirectoryService directory;
    @Autowired private ManagementApplicationWriteService writes;
    @Autowired private ApplicationSynchronizationService service;
    @Autowired private ApplicationSyncStore store;
    @Autowired private ApplicationSyncProperties properties;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private AdminRoleMapper roles;
    @Autowired private ApplicationSyncPolicy policy;
    @Autowired private SsoTicketService tickets;
    @Autowired private SsoTicketController ticketController;
    @MockitoBean private ApplicationSyncTransport transport;
    private String userId;
    private String appCode;

    @BeforeEach
    void setup() {
        appCode = "sync-" + UUID.randomUUID().toString().substring(0, 8);
        userId = directory.createManagedUser(new SaveUserCommand(appCode, appCode + "@example.invalid",
                "Synthetic", "001"), "admin-user").getId();
        directory.createManagedApplication(new SaveApplicationCommand(appCode, "Synthetic",
                "https://example.invalid", "https://example.invalid/callback", "https://example.invalid/permissions",
                "https://example.invalid", "default", "sso,user_sync", "synthetic-secret", 0), "admin-user");
        properties.setEnabled(true);
        properties.getTargets().put(appCode, URI.create("https://example.invalid"));
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return result(call.getArgument(0), "local-" + userId);
        }).when(transport).send(any(), any());
    }

    @AfterEach
    void stopDelivery() { properties.setEnabled(false); properties.getTargets().clear(); }

    @Test
    void deliversToSyntheticHttpProviderAndPersistsConfirmation() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/account-integration/users/" + userId, exchange -> {
            var request = json.readTree(exchange.getRequestBody());
            var reply = AccountUserSyncResult.builder().appCode(request.get("appCode").asText())
                    .globalUserId(request.get("globalUserId").asText()).localUserId("synthetic-http-user")
                    .appliedStatus(request.get("desiredStatus").asText()).appliedVersion(request.get("syncVersion").asLong())
                    .resultCode("APPLIED").build();
            byte[] body = json.writeValueAsBytes(reply);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            properties.getTargets().put(appCode, base);
            jdbc.update("update account_applications set notify_base_url = ? where app_code = ?", base.toString(), appCode);
            var http = new ManagedApplicationSyncTransport(policy, json, new HmacSignatureService(), Clock.systemUTC());
            doAnswer(call -> http.send(call.getArgument(0), call.getArgument(1))).when(transport).send(any(), any());
            var access = change("enabled", 0, false);
            service.process(access.getSyncCommandId());
            assertThat(store.find(access.getSyncCommandId()).getStatus()).isEqualTo("succeeded");
            assertThat(store.state(userId, appCode).getLocalUserId()).isEqualTo("synthetic-http-user");
            assertThat(directory.isAuthorized(userId, appCode)).isTrue();
        } finally { server.stop(0); }
    }

    @Test
    void refusesPreviouslyIssuedTicketAfterAccessRevocation() {
        service.process(change("enabled", 0, false).getSyncCommandId());
        String code = tickets.issue(appCode, new AccountUserSnapshot(userId, appCode,
                "synthetic@example.invalid", "Synthetic", "001", "default"));
        change("disabled", 1, false);
        var exchange = new SsoTicketController.ExchangeTicketRequest();
        exchange.setAppCode(appCode);
        exchange.setCode(code);
        assertThatThrownBy(() -> ticketController.exchange(exchange)).isInstanceOf(SsoTicketException.class);
    }

    @Test
    void confirmsMappingAndRequiresMatchingVersionEvenWhenDeliveryIsSwitchedOff() {
        var access = change("enabled", 0, false);
        assertThat(access.getIntegrationStatus()).isEqualTo("pending");
        assertThat(directory.isAuthorized(userId, appCode)).isFalse();
        service.process(access.getSyncCommandId());
        var state = store.state(userId, appCode);
        assertThat(state.getLocalUserId()).isEqualTo("local-" + userId);
        assertThat(state.getAppliedVersion()).isEqualTo(1);
        assertThat(state.getCreatedBy()).isEqualTo("admin-user");
        assertThat(directory.isAuthorized(userId, appCode)).isTrue();
        assertThat(store.find(access.getSyncCommandId()).getPayloadJson()).isNull();
        var listed = directory.getUserApplicationAccess(userId).stream()
                .filter(row -> appCode.equals(row.getAppCode())).findFirst().orElseThrow();
        assertThat(listed.getIntegrationStatus()).isEqualTo("succeeded");
        assertThat(listed.getLastSyncedAt()).isNotNull();
        properties.setEnabled(false);
        var disabled = change("disabled", 1, false);
        assertThat(disabled.getAppliedVersion()).isEqualTo(1);
        assertThat(directory.getApplication(appCode).isManagedSync()).isTrue();
        assertThat(directory.isAuthorized(userId, appCode)).isFalse();
        assertThatThrownBy(() -> change("enabled", 2, false)).isInstanceOf(ApiException.class);
        var enabled = change("enabled", 2, true);
        assertThat(enabled.getVersion()).isEqualTo(3);
        assertThat(directory.isAuthorized(userId, appCode)).isFalse();
        properties.setEnabled(true);
        service.retry(userId, appCode, 3, "admin-user");
        service.process(enabled.getSyncCommandId());
        assertThat(directory.isAuthorized(userId, appCode)).isTrue();
        jdbc.update("update account_users set status = 'disabled' where id = ?", userId);
        assertThat(directory.isAuthorized(userId, appCode)).isFalse();
    }

    @Test
    void retriesFixedPayloadAndStopsAfterFiveAttempts() {
        doThrow(new ApplicationSyncFailure("SYNC_DEPENDENCY_UNAVAILABLE", true)).when(transport).send(any(), any());
        var access = change("enabled", 0, false);
        var first = store.find(access.getSyncCommandId());
        directory.updateUserFields(userId, new SaveUserCommand(appCode, "changed@example.invalid", "Changed", "002"));
        for (int attempt = 1; attempt <= 5; attempt++) {
            jdbc.update("update account_sync_commands set next_retry_at = null where id = ?", first.getId());
            service.process(first.getId());
            var current = store.find(first.getId());
            assertThat(current.getAttempts()).isEqualTo(attempt);
            assertThat(current.getPayloadJson()).isEqualTo(first.getPayloadJson());
            assertThat(current.getIdempotencyKey()).isEqualTo(first.getIdempotencyKey());
            assertThat(current.getStatus()).isEqualTo(attempt < 5 ? "retry_wait" : "failed");
        }
        assertThat(directory.isAuthorized(userId, appCode)).isFalse();
        service.retry(userId, appCode, 1, "admin-user");
        assertThat(store.find(first.getId()).getPayloadJson()).isEqualTo(first.getPayloadJson());
        assertThat(store.find(first.getId()).getAttempts()).isZero();
        jdbc.update("update account_sync_commands set created_at = timestamp '2000-01-01 00:00:00' where id = ?", first.getId());
        service.process(first.getId());
        assertThat(store.find(first.getId()).getStatus()).isEqualTo("expired");
        assertThat(store.find(first.getId()).getPayloadJson()).isNull();
        assertThatThrownBy(() -> service.retry(userId, appCode, 1, "admin-user")).isInstanceOf(ApiException.class);
    }

    @Test
    void ignoresOldResponseAfterNewDesiredVersionAndExpiredLease() {
        var access = change("enabled", 0, false);
        doAnswer(call -> {
            change("disabled", 1, false);
            return result(call.getArgument(0), "local-old");
        }).when(transport).send(any(), any());
        service.process(access.getSyncCommandId());
        assertThat(store.find(access.getSyncCommandId()).getStatus()).isEqualTo("superseded");
        assertThat(store.state(userId, appCode)).isNull();
        var latest = store.latest(userId, appCode, 2);
        doAnswer(call -> {
            jdbc.update("update account_sync_commands set lease_token = 'new-owner' where id = ?", latest.getId());
            return result(call.getArgument(0), "local-stale");
        }).when(transport).send(any(), any());
        service.process(latest.getId());
        assertThat(store.state(userId, appCode)).isNull();
        jdbc.update("update account_sync_commands set lease_expires_at = timestamp '2000-01-01 00:00:00' where id = ?", latest.getId());
        doAnswer(call -> result(call.getArgument(0), "local-current")).when(transport).send(any(), any());
        service.process(latest.getId());
        assertThat(store.state(userId, appCode).getAppliedStatus()).isEqualTo("disabled");
        assertThat(directory.isAuthorized(userId, appCode)).isFalse();
    }

    @Test
    void onlyOneWorkerDeliversAnActiveLease() throws Exception {
        var access = change("enabled", 0, false);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var sends = new AtomicInteger();
        doAnswer(call -> {
            sends.incrementAndGet();
            entered.countDown();
            assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            return result(call.getArgument(0), "local-concurrent");
        }).when(transport).send(any(), any());
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> service.process(access.getSyncCommandId()));
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                workers.submit(() -> service.process(access.getSyncCommandId())).get(3, TimeUnit.SECONDS);
            } finally { release.countDown(); }
            first.get(3, TimeUnit.SECONDS);
        }
        assertThat(sends).hasValue(1);
        assertThat(store.find(access.getSyncCommandId()).getStatus()).isEqualTo("succeeded");
    }

    @Test
    void rejectsCrossUserLocalIdentityAndChangedTarget() {
        doAnswer(call -> result(call.getArgument(0), "shared-local")).when(transport).send(any(), any());
        service.process(change("enabled", 0, false).getSyncCommandId());
        String other = directory.createManagedUser(new SaveUserCommand(appCode + "-other", "other@example.invalid",
                "Other", "002"), "admin-user").getId();
        var access = writes.changeAccess(other, appCode, "enabled", 0, "synthetic", "admin-user");
        service.process(access.getSyncCommandId());
        assertThat(store.find(access.getSyncCommandId()).getErrorCode()).isEqualTo("SYNC_IDENTITY_CONFLICT");
        assertThat(store.state(other, appCode)).isNull();
        var disabled = change("disabled", 1, false);
        jdbc.update("update account_applications set version = version + 1 where app_code = ?", appCode);
        service.process(disabled.getSyncCommandId());
        assertThat(store.find(disabled.getSyncCommandId()).getErrorCode()).isEqualTo("SYNC_TARGET_CHANGED");
        assertThat(directory.isAuthorized(userId, appCode)).isFalse();
    }

    @Test
    void retryEndpointEnforcesSessionCsrfRoleVersionAndIdempotency() throws Exception {
        properties.setEnabled(false);
        var access = change("enabled", 0, false);
        String path = "/api/users/" + userId + "/applications/" + appCode + "/synchronizations";
        var anonymous = mvc.perform(get("/api/session")).andReturn();
        var session = (MockHttpSession) anonymous.getRequest().getSession(false);
        String csrf = json.readTree(anonymous.getResponse().getContentAsString()).get("csrfToken").asText();
        mvc.perform(post(path).session(session).header(CsrfTokenManager.HEADER_NAME, csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isUnauthorized());
        session.setAttribute(AuthController.SESSION_USER_KEY, new AccountSessionUser(userId, appCode, "Synthetic"));
        roles.insert(userId, "ACCOUNT_AUDITOR");
        mvc.perform(post(path).session(session).header(CsrfTokenManager.HEADER_NAME, csrf)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
        roles.insert(userId, "ACCOUNT_ADMIN");
        mvc.perform(post(path).session(session).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).session(session).header(CsrfTokenManager.HEADER_NAME, csrf)
                .header("Idempotency-Key", "off-" + appCode).header("Accept-Language", "en-US")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SYNC_DISABLED"))
                .andExpect(jsonPath("$.message").value("The current user, application or synchronization target configuration does not allow delivery."));
        properties.setEnabled(true);
        mvc.perform(post(path).session(session).header(CsrfTokenManager.HEADER_NAME, csrf)
                .header("Idempotency-Key", "stale-" + appCode)
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":2}"))
                .andExpect(status().isConflict());
        for (int replay = 0; replay < 2; replay++) {
            mvc.perform(post(path).session(session).header(CsrfTokenManager.HEADER_NAME, csrf)
                    .header("Idempotency-Key", "retry-" + appCode)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1}"))
                    .andExpect(status().isAccepted()).andExpect(jsonPath("$.version").value(1))
                    .andExpect(jsonPath("$.syncCommandId").value(access.getSyncCommandId()))
                    .andExpect(jsonPath("$.integrationStatus").value("pending"));
        }
        assertThat(jdbc.queryForObject("select count(*) from account_operation_logs where operation_type = 'APPLICATION_SYNC_RETRIED' and target_id = ?",
                Integer.class, userId + ":" + appCode)).isEqualTo(1);
    }

    private ApplicationAccess change(String desired, long version, boolean reuse) {
        return writes.changeAccess(userId, appCode, desired, version, "synthetic", "admin-user", reuse);
    }

    private AccountUserSyncResult result(ApplicationSyncDelivery delivery, String localId) {
        return AccountUserSyncResult.builder().appCode(delivery.getAppCode()).globalUserId(delivery.getUserId())
                .localUserId(localId).appliedStatus(delivery.getDesiredStatus()).appliedVersion(delivery.getSyncVersion())
                .resultCode("APPLIED").build();
    }
}
