package com.hypers.account.integration.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MenuPermissionContractValidator {

    private static final Set<String> NODE_TYPES = new HashSet<String>(
            Arrays.asList("GROUP", "MENU", "ACTION"));

    void validateQuery(String expectedAppCode, String protocolHeader, MenuPermissionQuery query) {
        if (!AccountMenuPermissionProtocol.VERSION.equals(protocolHeader)
                || query == null
                || !AccountMenuPermissionProtocol.VERSION.equals(query.getProtocolVersion())) {
            fail(426, "PROTOCOL_VERSION_UNSUPPORTED");
        }
        validateSubject(expectedAppCode, query);
    }

    void validateReplace(String expectedAppCode,
                         String protocolHeader,
                         String idempotencyKey,
                         MenuPermissionReplaceCommand command) {
        if (!AccountMenuPermissionProtocol.VERSION.equals(protocolHeader)
                || command == null
                || !AccountMenuPermissionProtocol.VERSION.equals(command.getProtocolVersion())) {
            fail(426, "PROTOCOL_VERSION_UNSUPPORTED");
        }
        validateSubject(expectedAppCode, command);
        if (!printableAscii(idempotencyKey, 16, 128)
                || !text(command.getExpectedCatalogRevision(), 1, 256)
                || !text(command.getExpectedPermissionRevision(), 1, 256)
                || command.getSelectedCodes() == null
                || command.getSelectedCodes().size() > AccountMenuPermissionProtocol.MAX_SELECTED_CODES
                || !validCodes(command.getSelectedCodes())) {
            fail(422, "PERMISSION_CODE_INVALID");
        }
    }

    MenuPermissionSnapshot normalizeSnapshot(MenuPermissionSnapshot snapshot) {
        if (snapshot == null
                || !text(snapshot.getCatalogRevision(), 1, 256)
                || !text(snapshot.getPermissionRevision(), 1, 256)
                || !AccountMenuPermissionProtocol.ASSIGNMENT_MODE_ADDITIVE.equals(snapshot.getAssignmentMode())
                || snapshot.getNodes() == null
                || snapshot.getSelectedCodes() == null
                || snapshot.getInheritedCodes() == null
                || snapshot.getNodes().size() > AccountMenuPermissionProtocol.MAX_NODES
                || snapshot.getSelectedCodes().size() > AccountMenuPermissionProtocol.MAX_SELECTED_CODES
                || snapshot.getInheritedCodes().size() > AccountMenuPermissionProtocol.MAX_INHERITED_CODES) {
            fail(422, "CATALOG_INVALID");
        }
        final Map<String, MenuPermissionNode> byCode = new HashMap<String, MenuPermissionNode>();
        for (MenuPermissionNode node : snapshot.getNodes()) {
            validateNode(node);
            if (byCode.put(node.getCode(), node) != null) fail(422, "CATALOG_INVALID");
        }
        for (MenuPermissionNode node : snapshot.getNodes()) {
            if (node.getParentCode() != null && !byCode.containsKey(node.getParentCode())) {
                fail(422, "CATALOG_INVALID");
            }
            validateDepth(node, byCode);
        }
        if (!validCodes(snapshot.getSelectedCodes()) || !validCodes(snapshot.getInheritedCodes())) {
            fail(422, "CATALOG_INVALID");
        }
        for (String code : snapshot.getSelectedCodes()) {
            MenuPermissionNode node = byCode.get(code);
            if (node == null || !node.isAssignable()) fail(422, "CATALOG_INVALID");
        }
        for (String code : snapshot.getInheritedCodes()) {
            if (!byCode.containsKey(code)) fail(422, "CATALOG_INVALID");
        }
        List<MenuPermissionNode> nodes = new ArrayList<MenuPermissionNode>(snapshot.getNodes());
        Collections.sort(nodes, new Comparator<MenuPermissionNode>() {
            @Override
            public int compare(MenuPermissionNode left, MenuPermissionNode right) {
                int bySort = Integer.compare(left.getSort(), right.getSort());
                return bySort == 0 ? left.getCode().compareTo(right.getCode()) : bySort;
            }
        });
        snapshot.setNodes(nodes);
        snapshot.setSelectedCodes(new ArrayList<String>(snapshot.getSelectedCodes()));
        snapshot.setInheritedCodes(new ArrayList<String>(snapshot.getInheritedCodes()));
        return snapshot;
    }

    private void validateSubject(String expectedAppCode, MenuPermissionQuery query) {
        if (!expectedAppCode.equals(query.getAppCode())
                || !printableAscii(query.getAppCode(), 1, 128)
                || !text(query.getGlobalUserId(), 1, 128)
                || !text(query.getLocalUserId(), 1, 128)
                || !text(query.getAccount(), 1, 128)
                || !text(query.getLocale(), 2, 32)) {
            fail(422, "VALIDATION_FAILED");
        }
    }

    private void validateNode(MenuPermissionNode node) {
        if (node == null
                || !printableAscii(node.getCode(), 1, 128)
                || (node.getParentCode() != null && !printableAscii(node.getParentCode(), 1, 128))
                || !NODE_TYPES.contains(node.getNodeType())
                || !text(node.getDefaultName(), 1, 256)
                || node.getLocalizedNames() == null
                || node.getLocalizedNames().size() > 16) {
            fail(422, "CATALOG_INVALID");
        }
        for (Map.Entry<String, String> entry : node.getLocalizedNames().entrySet()) {
            if (!text(entry.getKey(), 2, 32) || !text(entry.getValue(), 1, 256)) {
                fail(422, "CATALOG_INVALID");
            }
        }
    }

    private void validateDepth(MenuPermissionNode node, Map<String, MenuPermissionNode> byCode) {
        Set<String> visited = new HashSet<String>();
        MenuPermissionNode current = node;
        int depth = 0;
        while (current.getParentCode() != null) {
            if (!visited.add(current.getCode()) || ++depth > AccountMenuPermissionProtocol.MAX_TREE_DEPTH) {
                fail(422, "CATALOG_INVALID");
            }
            current = byCode.get(current.getParentCode());
            if (current == null) fail(422, "CATALOG_INVALID");
        }
    }

    private boolean validCodes(List<String> codes) {
        Set<String> unique = new LinkedHashSet<String>();
        for (String code : codes) {
            if (!printableAscii(code, 1, 128) || !unique.add(code)) return false;
        }
        return true;
    }

    private boolean text(String value, int min, int max) {
        return value != null && value.length() >= min && value.length() <= max
                && !value.trim().isEmpty();
    }

    private boolean printableAscii(String value, int min, int max) {
        if (!text(value, min, max)) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character > 0x7e) return false;
        }
        return true;
    }

    private void fail(int status, String code) {
        throw new AccountIntegrationException(status, code);
    }
}
