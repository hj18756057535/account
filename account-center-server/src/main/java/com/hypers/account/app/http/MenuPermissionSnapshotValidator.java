package com.hypers.account.app.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypers.account.app.ApplicationMenuPermissionFailure;
import com.hypers.account.contract.MenuPermissionProtocol;
import com.hypers.account.contract.application.MenuPermissionNode;
import com.hypers.account.contract.application.MenuPermissionSnapshot;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;

@UtilityClass
class MenuPermissionSnapshotValidator {

    private static final Set<String> NODE_TYPES = new HashSet<>(Arrays.asList("GROUP", "MENU", "ACTION"));
    private static final Set<String> SNAPSHOT_FIELDS = new HashSet<>(Arrays.asList(
            "catalogRevision", "permissionRevision", "assignmentMode", "nodes",
            "selectedCodes", "inheritedCodes"));
    private static final Set<String> NODE_FIELDS = new HashSet<>(Arrays.asList(
            "code", "parentCode", "nodeType", "defaultName", "localizedNames", "assignable", "sort"));
    private static final Set<String> REQUIRED_NODE_FIELDS = new HashSet<>(Arrays.asList(
            "code", "nodeType", "defaultName", "localizedNames", "assignable", "sort"));

    static void validateJson(JsonNode tree) {
        if (!onlyFields(tree, SNAPSHOT_FIELDS) || !hasFields(tree, SNAPSHOT_FIELDS)
                || !tree.get("catalogRevision").isTextual()
                || !tree.get("permissionRevision").isTextual()
                || !tree.get("assignmentMode").isTextual()
                || !tree.get("nodes").isArray()
                || !textArray(tree.get("selectedCodes"))
                || !textArray(tree.get("inheritedCodes"))) fail();
        for (JsonNode node : tree.get("nodes")) {
            if (!node.isObject() || !onlyFields(node, NODE_FIELDS) || !hasFields(node, REQUIRED_NODE_FIELDS)
                    || !node.get("code").isTextual()
                    || node.has("parentCode") && !node.get("parentCode").isNull()
                    && !node.get("parentCode").isTextual()
                    || !node.get("nodeType").isTextual()
                    || !node.get("defaultName").isTextual()
                    || !node.get("localizedNames").isObject()
                    || !node.get("assignable").isBoolean()
                    || !node.get("sort").isIntegralNumber()
                    || !node.get("sort").canConvertToInt()) fail();
            java.util.Iterator<JsonNode> names = node.get("localizedNames").elements();
            while (names.hasNext()) if (!names.next().isTextual()) fail();
        }
    }

    static MenuPermissionSnapshot normalize(MenuPermissionSnapshot snapshot) {
        if (snapshot == null || !text(snapshot.getCatalogRevision(), 1, 256)
                || !text(snapshot.getPermissionRevision(), 1, 256)
                || !MenuPermissionProtocol.ASSIGNMENT_MODE_ADDITIVE.equals(snapshot.getAssignmentMode())
                || snapshot.getNodes() == null || snapshot.getSelectedCodes() == null
                || snapshot.getInheritedCodes() == null
                || snapshot.getNodes().size() > MenuPermissionProtocol.MAX_NODES
                || snapshot.getSelectedCodes().size() > MenuPermissionProtocol.MAX_SELECTED_CODES
                || snapshot.getInheritedCodes().size() > MenuPermissionProtocol.MAX_INHERITED_CODES) {
            fail();
        }
        Map<String, MenuPermissionNode> nodesByCode = new HashMap<>();
        for (MenuPermissionNode node : snapshot.getNodes()) {
            validateNode(node);
            if (nodesByCode.put(node.getCode(), node) != null) fail();
        }
        for (MenuPermissionNode node : snapshot.getNodes()) {
            if (node.getParentCode() != null && !nodesByCode.containsKey(node.getParentCode())) fail();
            validateDepth(node, nodesByCode);
        }
        validateCodes(snapshot.getSelectedCodes(), nodesByCode, true);
        validateCodes(snapshot.getInheritedCodes(), nodesByCode, false);

        List<MenuPermissionNode> nodes = new ArrayList<>(snapshot.getNodes());
        nodes.sort(Comparator.comparingInt(MenuPermissionNode::getSort).thenComparing(MenuPermissionNode::getCode));
        snapshot.setNodes(nodes);
        snapshot.setSelectedCodes(new ArrayList<>(snapshot.getSelectedCodes()));
        snapshot.setInheritedCodes(new ArrayList<>(snapshot.getInheritedCodes()));
        return snapshot;
    }

    private static void validateNode(MenuPermissionNode node) {
        if (node == null || !code(node.getCode())
                || (node.getParentCode() != null && !code(node.getParentCode()))
                || !NODE_TYPES.contains(node.getNodeType()) || !text(node.getDefaultName(), 1, 256)
                || node.getLocalizedNames() == null || node.getLocalizedNames().size() > 16) {
            fail();
        }
        for (Map.Entry<String, String> entry : node.getLocalizedNames().entrySet()) {
            if (!text(entry.getKey(), 2, 32) || !text(entry.getValue(), 1, 256)) fail();
        }
    }

    private static void validateCodes(List<String> codes,
                                      Map<String, MenuPermissionNode> nodesByCode,
                                      boolean requireAssignable) {
        Set<String> unique = new HashSet<>();
        for (String code : codes) {
            MenuPermissionNode node = nodesByCode.get(code);
            if (!code(code) || !unique.add(code) || node == null || requireAssignable && !node.isAssignable()) fail();
        }
    }

    private static void validateDepth(MenuPermissionNode node, Map<String, MenuPermissionNode> nodesByCode) {
        Set<String> visited = new HashSet<>();
        MenuPermissionNode current = node;
        int depth = 0;
        while (current.getParentCode() != null) {
            if (!visited.add(current.getCode()) || ++depth > MenuPermissionProtocol.MAX_TREE_DEPTH) fail();
            current = nodesByCode.get(current.getParentCode());
            if (current == null) fail();
        }
    }

    private static boolean code(String value) {
        if (!text(value, 1, 128)) return false;
        byte[] ascii = value.getBytes(StandardCharsets.US_ASCII);
        if (ascii.length != value.length()) return false;
        for (byte item : ascii) if (item < 0x21 || item > 0x7e) return false;
        return true;
    }

    private static boolean text(String value, int min, int max) {
        return value != null && value.length() >= min && value.length() <= max && !value.trim().isEmpty();
    }

    private static boolean onlyFields(JsonNode object, Set<String> allowed) {
        java.util.Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) if (!allowed.contains(fields.next())) return false;
        return true;
    }

    private static boolean hasFields(JsonNode object, Set<String> required) {
        for (String field : required) if (!object.has(field)) return false;
        return true;
    }

    private static boolean textArray(JsonNode array) {
        if (array == null || !array.isArray()) return false;
        for (JsonNode value : array) if (!value.isTextual()) return false;
        return true;
    }

    private static void fail() {
        throw new ApplicationMenuPermissionFailure(503, "PERMISSION_PROVIDER_UNAVAILABLE");
    }
}
