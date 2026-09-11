package com.hypers.account.contract;

import lombok.experimental.UtilityClass;

@UtilityClass
public class MenuPermissionProtocol {

    public static final String VERSION = "menu_permission_v1";
    public static final String ASSIGNMENT_MODE_ADDITIVE = "ADDITIVE";
    public static final int MAX_NODES = 10_000;
    public static final int MAX_SELECTED_CODES = 5_000;
    public static final int MAX_INHERITED_CODES = 10_000;
    public static final int MAX_TREE_DEPTH = 32;
    public static final int MAX_REQUEST_BYTES = 1024 * 1024;
}
