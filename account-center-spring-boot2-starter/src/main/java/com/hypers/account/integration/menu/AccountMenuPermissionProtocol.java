package com.hypers.account.integration.menu;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AccountMenuPermissionProtocol {

    public static final String VERSION = "menu_permission_v1";
    public static final String QUERY_PATH = "/account-integration/v1/menu-permissions/query";
    public static final String REPLACE_PATH = "/account-integration/v1/menu-permissions";
    public static final String APP_CODE_HEADER = "X-Account-App-Code";
    public static final String TIMESTAMP_HEADER = "X-Account-Timestamp";
    public static final String NONCE_HEADER = "X-Account-Nonce";
    public static final String SIGNATURE_HEADER = "X-Account-Signature";
    public static final String PROTOCOL_VERSION_HEADER = "X-Account-Protocol-Version";
    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    public static final String ASSIGNMENT_MODE_ADDITIVE = "ADDITIVE";
    public static final int MAX_NODES = 10000;
    public static final int MAX_SELECTED_CODES = 5000;
    public static final int MAX_INHERITED_CODES = 10000;
    public static final int MAX_TREE_DEPTH = 32;
    public static final int MAX_REQUEST_BYTES = 1024 * 1024;
}
