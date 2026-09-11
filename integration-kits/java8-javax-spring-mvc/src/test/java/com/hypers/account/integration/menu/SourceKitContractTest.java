package com.hypers.account.integration.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SourceKitContractTest {

    @Test
    void manifestMatchesAllCopyableSources() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode files = objectMapper.readTree(Files.readAllBytes(
                Paths.get("SOURCE-KIT-MANIFEST.json"))).get("files");
        int checked = 0;
        java.util.Iterator<String> names = files.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(Paths.get(name)));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value & 0xff));
            assertEquals(files.get(name).asText(), hex.toString(), name);
            checked++;
        }
        assertEquals(17, checked);
    }

    @Test
    void matchesSharedSignatureVector() throws Exception {
        JsonNode vector = new ObjectMapper().readTree(Files.readAllBytes(
                Paths.get("..", "conformance", "menu_permission_v1.json")));
        AccountHmacSigner signer = new AccountHmacSigner();
        String signText = signer.buildSignText(
                vector.get("method").asText(),
                vector.get("path").asText(),
                vector.get("timestamp").asText(),
                vector.get("nonce").asText(),
                vector.get("body").asText());
        assertEquals(vector.get("signature").asText(),
                signer.sign(signText, vector.get("secret").asText()));
    }

    @Test
    void acceptsSignedRequestAndRejectsNonceReplay() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AccountHmacSigner signer = new AccountHmacSigner();
        AccountMenuPermissionSignatureFilter filter = new AccountMenuPermissionSignatureFilter(
                "synthetic-app", "synthetic-secret", 300000L,
                new InMemoryAccountNonceStore(), signer, objectMapper);
        String body = "{\"protocolVersion\":\"menu_permission_v1\"}";
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = "nonce-source-kit-001";
        String signature = signer.sign(signer.buildSignText(
                "POST", AccountMenuPermissionProtocol.QUERY_PATH, timestamp, nonce, body),
                "synthetic-secret");

        MockHttpServletRequest request = request(body, timestamp, nonce, signature);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());

        MockHttpServletResponse replay = new MockHttpServletResponse();
        filter.doFilter(request(body, timestamp, nonce, signature), replay, new MockFilterChain());
        assertEquals(401, replay.getStatus());
    }

    @Test
    void mapsNonceStoreFailureToProviderUnavailable() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AccountHmacSigner signer = new AccountHmacSigner();
        AccountNonceStore failedStore = (appCode, nonce, expiresAt) -> {
            throw new IllegalStateException("redis unavailable");
        };
        AccountMenuPermissionSignatureFilter filter = new AccountMenuPermissionSignatureFilter(
                "synthetic-app", "synthetic-secret", 300000L, failedStore, signer, objectMapper);
        String body = "{\"protocolVersion\":\"menu_permission_v1\"}";
        String timestamp = Long.toString(System.currentTimeMillis());
        String nonce = "nonce-source-kit-503";
        String signature = signer.sign(signer.buildSignText(
                "POST", AccountMenuPermissionProtocol.QUERY_PATH, timestamp, nonce, body),
                "synthetic-secret");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(body, timestamp, nonce, signature), response, new MockFilterChain());

        assertEquals(503, response.getStatus());
    }

    @Test
    void rejectsExtremeTimestampWithoutOverflowingTheClockWindow() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AccountHmacSigner signer = new AccountHmacSigner();
        AccountMenuPermissionSignatureFilter filter = new AccountMenuPermissionSignatureFilter(
                "synthetic-app", "synthetic-secret", 300000L,
                new InMemoryAccountNonceStore(), signer, objectMapper);
        String body = "{\"protocolVersion\":\"menu_permission_v1\"}";
        String timestamp = Long.toString(Long.MIN_VALUE);
        String nonce = "nonce-source-kit-extreme";
        String signature = signer.sign(signer.buildSignText(
                "POST", AccountMenuPermissionProtocol.QUERY_PATH, timestamp, nonce, body),
                "synthetic-secret");

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(body, timestamp, nonce, signature), response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void normalizesCatalogAndRejectsDuplicateSelection() {
        final MenuPermissionSnapshot snapshot = snapshot();
        AccountMenuPermissionController controller = new AccountMenuPermissionController(
                "synthetic-app", new AccountMenuPermissionHandler() {
                    @Override public MenuPermissionSnapshot query(MenuPermissionQuery query) { return snapshot; }
                    @Override public MenuPermissionSnapshot replace(
                            String idempotencyKey, MenuPermissionReplaceCommand command) { return snapshot; }
                });
        MenuPermissionSnapshot result = controller.query(
                AccountMenuPermissionProtocol.VERSION, query());
        assertEquals("menu", result.getNodes().get(0).getCode());

        MenuPermissionReplaceCommand command = command();
        List<String> duplicate = new ArrayList<String>();
        duplicate.add("report:view");
        duplicate.add("report:view");
        command.setSelectedCodes(duplicate);
        try {
            controller.replace(AccountMenuPermissionProtocol.VERSION, "idempotency-key-0001", command);
        } catch (AccountIntegrationException exception) {
            assertEquals(422, exception.getStatus());
            assertEquals("PERMISSION_CODE_INVALID", exception.getCode());
            return;
        }
        throw new AssertionError("Expected duplicate selection to be rejected");
    }

    private MockHttpServletRequest request(
            String body, String timestamp, String nonce, String signature) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", AccountMenuPermissionProtocol.QUERY_PATH);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        request.addHeader(AccountMenuPermissionProtocol.PROTOCOL_VERSION_HEADER,
                AccountMenuPermissionProtocol.VERSION);
        request.addHeader(AccountMenuPermissionProtocol.APP_CODE_HEADER, "synthetic-app");
        request.addHeader(AccountMenuPermissionProtocol.TIMESTAMP_HEADER, timestamp);
        request.addHeader(AccountMenuPermissionProtocol.NONCE_HEADER, nonce);
        request.addHeader(AccountMenuPermissionProtocol.SIGNATURE_HEADER, signature);
        return request;
    }

    private MenuPermissionQuery query() {
        MenuPermissionQuery query = new MenuPermissionQuery();
        query.setProtocolVersion(AccountMenuPermissionProtocol.VERSION);
        query.setAppCode("synthetic-app");
        query.setGlobalUserId("user-1");
        query.setLocalUserId("local-1");
        query.setAccount("user.one");
        query.setLocale("zh-CN");
        return query;
    }

    private MenuPermissionReplaceCommand command() {
        MenuPermissionReplaceCommand command = new MenuPermissionReplaceCommand();
        MenuPermissionQuery query = query();
        command.setProtocolVersion(query.getProtocolVersion());
        command.setAppCode(query.getAppCode());
        command.setGlobalUserId(query.getGlobalUserId());
        command.setLocalUserId(query.getLocalUserId());
        command.setAccount(query.getAccount());
        command.setLocale(query.getLocale());
        command.setExpectedCatalogRevision("catalog-1");
        command.setExpectedPermissionRevision("permission-1");
        command.setSelectedCodes(Collections.singletonList("report:view"));
        return command;
    }

    private MenuPermissionSnapshot snapshot() {
        MenuPermissionNode action = new MenuPermissionNode();
        action.setCode("report:view");
        action.setParentCode("menu");
        action.setNodeType("ACTION");
        action.setDefaultName("View report");
        action.setAssignable(true);
        action.setSort(20);
        MenuPermissionNode menu = new MenuPermissionNode();
        menu.setCode("menu");
        menu.setNodeType("MENU");
        menu.setDefaultName("Menu");
        menu.setAssignable(true);
        menu.setSort(10);
        MenuPermissionSnapshot snapshot = new MenuPermissionSnapshot();
        snapshot.setCatalogRevision("catalog-1");
        snapshot.setPermissionRevision("permission-1");
        snapshot.setAssignmentMode(AccountMenuPermissionProtocol.ASSIGNMENT_MODE_ADDITIVE);
        List<MenuPermissionNode> nodes = new ArrayList<MenuPermissionNode>();
        nodes.add(action);
        nodes.add(menu);
        snapshot.setNodes(nodes);
        snapshot.setSelectedCodes(Collections.singletonList("report:view"));
        snapshot.setInheritedCodes(Collections.singletonList("menu"));
        return snapshot;
    }
}
