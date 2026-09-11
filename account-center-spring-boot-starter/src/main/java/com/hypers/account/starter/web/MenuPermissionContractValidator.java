package com.hypers.account.starter.web;

import com.hypers.account.contract.MenuPermissionProtocol;
import com.hypers.account.contract.application.MenuPermissionNode;
import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

final class MenuPermissionContractValidator {

    private static final Set<String> NODE_TYPES = Set.of("GROUP", "MENU", "ACTION");

    void validateQuery(String expectedAppCode, String protocolHeader, MenuPermissionQuery query) {
        if (!MenuPermissionProtocol.VERSION.equals(protocolHeader)
                || query == null
                || !MenuPermissionProtocol.VERSION.equals(query.getProtocolVersion())) {
            fail(HttpStatus.UPGRADE_REQUIRED, "PROTOCOL_VERSION_UNSUPPORTED");
        }
        validateSubject(expectedAppCode, query.getAppCode(), query.getGlobalUserId(),
                query.getLocalUserId(), query.getAccount(), query.getLocale());
    }

    void validateReplace(String expectedAppCode,
                         String protocolHeader,
                         String idempotencyKey,
                         MenuPermissionReplaceCommand command) {
        if (!MenuPermissionProtocol.VERSION.equals(protocolHeader)
                || command == null
                || !MenuPermissionProtocol.VERSION.equals(command.getProtocolVersion())) {
            fail(HttpStatus.UPGRADE_REQUIRED, "PROTOCOL_VERSION_UNSUPPORTED");
        }
        validateSubject(expectedAppCode, command.getAppCode(), command.getGlobalUserId(),
                command.getLocalUserId(), command.getAccount(), command.getLocale());
        if (!printableAscii(idempotencyKey, 16, 128)
                || !text(command.getExpectedCatalogRevision(), 1, 256)
                || !text(command.getExpectedPermissionRevision(), 1, 256)
                || command.getSelectedCodes() == null
                || command.getSelectedCodes().size() > MenuPermissionProtocol.MAX_SELECTED_CODES
                || !validCodes(command.getSelectedCodes(), true)) {
            fail(HttpStatus.UNPROCESSABLE_ENTITY, "PERMISSION_CODE_INVALID");
        }
    }

    MenuPermissionSnapshot normalizeSnapshot(MenuPermissionSnapshot snapshot) {
        if (snapshot == null
                || !text(snapshot.getCatalogRevision(), 1, 256)
                || !text(snapshot.getPermissionRevision(), 1, 256)
                || !MenuPermissionProtocol.ASSIGNMENT_MODE_ADDITIVE.equals(snapshot.getAssignmentMode())
                || snapshot.getNodes() == null
                || snapshot.getSelectedCodes() == null
                || snapshot.getInheritedCodes() == null
                || snapshot.getNodes().size() > MenuPermissionProtocol.MAX_NODES
                || snapshot.getSelectedCodes().size() > MenuPermissionProtocol.MAX_SELECTED_CODES
                || snapshot.getInheritedCodes().size() > MenuPermissionProtocol.MAX_INHERITED_CODES) {
            fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
        }

        Map<String, MenuPermissionNode> byCode = new HashMap<>();
        for (MenuPermissionNode node : snapshot.getNodes()) {
            validateNode(node);
            if (byCode.put(node.getCode(), node) != null) {
                fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
            }
        }
        for (MenuPermissionNode node : snapshot.getNodes()) {
            if (node.getParentCode() != null && !byCode.containsKey(node.getParentCode())) {
                fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
            }
            validateDepth(node, byCode);
        }
        if (!validCodes(snapshot.getSelectedCodes(), true)
                || !validCodes(snapshot.getInheritedCodes(), true)) {
            fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
        }
        for (String code : snapshot.getSelectedCodes()) {
            MenuPermissionNode node = byCode.get(code);
            if (node == null || !node.isAssignable()) {
                fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
            }
        }
        for (String code : snapshot.getInheritedCodes()) {
            if (!byCode.containsKey(code)) {
                fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
            }
        }

        List<MenuPermissionNode> nodes = new ArrayList<>(snapshot.getNodes());
        nodes.sort(Comparator.comparingInt(MenuPermissionNode::getSort)
                .thenComparing(MenuPermissionNode::getCode));
        snapshot.setNodes(nodes);
        snapshot.setSelectedCodes(new ArrayList<>(snapshot.getSelectedCodes()));
        snapshot.setInheritedCodes(new ArrayList<>(snapshot.getInheritedCodes()));
        return snapshot;
    }

    private void validateSubject(String expectedAppCode,
                                 String appCode,
                                 String globalUserId,
                                 String localUserId,
                                 String account,
                                 String locale) {
        if (!expectedAppCode.equals(appCode)
                || !printableAscii(appCode, 1, 128)
                || !text(globalUserId, 1, 128)
                || !text(localUserId, 1, 128)
                || !text(account, 1, 128)
                || !text(locale, 2, 32)) {
            fail(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED");
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
            fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
        }
        for (Map.Entry<String, String> localizedName : node.getLocalizedNames().entrySet()) {
            if (!text(localizedName.getKey(), 2, 32) || !text(localizedName.getValue(), 1, 256)) {
                fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
            }
        }
    }

    private void validateDepth(MenuPermissionNode node, Map<String, MenuPermissionNode> byCode) {
        Set<String> visited = new HashSet<>();
        MenuPermissionNode current = node;
        int depth = 0;
        while (current.getParentCode() != null) {
            if (!visited.add(current.getCode()) || ++depth > MenuPermissionProtocol.MAX_TREE_DEPTH) {
                fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
            }
            current = byCode.get(current.getParentCode());
            if (current == null) {
                fail(HttpStatus.UNPROCESSABLE_ENTITY, "CATALOG_INVALID");
            }
        }
    }

    private boolean validCodes(List<String> codes, boolean unique) {
        Set<String> values = unique ? new LinkedHashSet<>() : null;
        for (String code : codes) {
            if (!printableAscii(code, 1, 128) || (unique && !values.add(code))) {
                return false;
            }
        }
        return true;
    }

    private boolean text(String value, int min, int max) {
        return value != null && value.length() >= min && value.length() <= max && !value.isBlank();
    }

    private boolean printableAscii(String value, int min, int max) {
        if (!text(value, min, max)) return false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x20 || character > 0x7e) return false;
        }
        return true;
    }

    private void fail(HttpStatus status, String code) {
        throw new AccountIntegrationException(status, code, code);
    }
}
