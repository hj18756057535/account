package com.hypers.account.boot2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.hypers.account.integration.AccountProviderProtocol;
import com.hypers.account.integration.directory.AccountDirectoryPage;
import com.hypers.account.integration.directory.AccountDirectoryUser;
import com.hypers.account.integration.menu.AccountMenuPermissionProtocol;
import com.hypers.account.integration.menu.security.AccountHmacSigner;
import com.hypers.account.integration.menu.security.AccountMenuPermissionSignatureFilter;
import com.hypers.account.integration.menu.security.InMemoryAccountNonceStore;
import com.hypers.account.integration.user.AccountUserSyncResult;
import com.hypers.account.integration.web.AccountProviderController;
import com.hypers.account.integration.web.AccountProviderExceptionHandler;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountProviderTest {
    private static final String SECRET = "synthetic-provider-secret";
    private final AccountHmacSigner signer = new AccountHmacSigner();
    private final AtomicInteger calls = new AtomicInteger();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        SimpleModule strings = new SimpleModule();
        strings.addSerializer(Long.class, ToStringSerializer.instance);
        strings.addSerializer(Long.TYPE, ToStringSerializer.instance);
        json.registerModule(strings);
        AccountProviderController controller = new AccountProviderController("cms-ai", query -> {
            calls.incrementAndGet();
            AccountDirectoryPage page = new AccountDirectoryPage();
            page.setTenantCode("default");
            AccountDirectoryUser user = new AccountDirectoryUser();
            user.setLocalUserId("9007199254740993");
            user.setAccount("fixture-user");
            user.setStatus("enabled");
            page.setUsers(Collections.singletonList(user));
            return page;
        }, (key, command) -> {
            AccountUserSyncResult result = new AccountUserSyncResult();
            result.setAppCode(command.getAppCode());
            result.setGlobalUserId(command.getGlobalUserId());
            result.setLocalUserId("9007199254740993");
            result.setAppliedStatus(command.getDesiredStatus());
            result.setAppliedVersion(command.getSyncVersion());
            result.setResultCode("APPLIED");
            return result;
        });
        AccountMenuPermissionSignatureFilter filter = new AccountMenuPermissionSignatureFilter("cms-ai", SECRET,
                300000, new InMemoryAccountNonceStore(), signer, json, AccountProviderProtocol.VERSION);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new AccountProviderExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).addFilter(filter).build();
    }

    @Test
    void unsignedDirectoryReadIsDeniedBeforeAccessingTheHost() throws Exception {
        mvc.perform(post(AccountProviderProtocol.DIRECTORY_PATH).contentType(MediaType.APPLICATION_JSON)
                        .header(AccountMenuPermissionProtocol.PROTOCOL_VERSION_HEADER, AccountProviderProtocol.VERSION)
                        .content("{\"kind\":\"users\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(calls.get()).isZero();
    }

    @Test
    void signedDirectoryReadPreservesLargeStringIdsAndRejectsReplayedNonce() throws Exception {
        String body = "{\"kind\":\"users\",\"limit\":1}";
        String nonce = UUID.randomUUID().toString();
        mvc.perform(signed(post(AccountProviderProtocol.DIRECTORY_PATH), "POST", AccountProviderProtocol.DIRECTORY_PATH, body, nonce))
                .andExpect(status().isOk()).andExpect(jsonPath("$.users[0].localUserId").value("9007199254740993"));
        mvc.perform(signed(post(AccountProviderProtocol.DIRECTORY_PATH), "POST", AccountProviderProtocol.DIRECTORY_PATH, body, nonce))
                .andExpect(status().isUnauthorized());
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void userSyncVersionRemainsNumericEvenWhenHostSerializesLongIdsAsStrings() throws Exception {
        String path = AccountProviderProtocol.USERS_PATH + "/global-user";
        String body = "{\"appCode\":\"cms-ai\",\"globalUserId\":\"global-user\",\"account\":\"fixture-user\","
                + "\"desiredStatus\":\"enabled\",\"syncVersion\":3}";
        mvc.perform(signed(put(path), "PUT", path, body, UUID.randomUUID().toString())
                        .header("Idempotency-Key", "fixture-sync-key"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.appliedVersion").isNumber())
                .andExpect(jsonPath("$.appliedVersion").value(3))
                .andExpect(jsonPath("$.localUserId").value("9007199254740993"));
    }

    private MockHttpServletRequestBuilder signed(MockHttpServletRequestBuilder request, String method,
                                                 String path, String body, String nonce) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return request.contentType(MediaType.APPLICATION_JSON).content(body)
                .header(AccountMenuPermissionProtocol.PROTOCOL_VERSION_HEADER, AccountProviderProtocol.VERSION)
                .header(AccountMenuPermissionProtocol.APP_CODE_HEADER, "cms-ai")
                .header(AccountMenuPermissionProtocol.TIMESTAMP_HEADER, timestamp)
                .header(AccountMenuPermissionProtocol.NONCE_HEADER, nonce)
                .header(AccountMenuPermissionProtocol.SIGNATURE_HEADER,
                        signer.sign(signer.buildSignText(method, path, timestamp, nonce, body), SECRET));
    }
}
