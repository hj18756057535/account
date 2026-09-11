package com.hypers.account.integration.menu;

public interface AccountMenuPermissionHandler {

    MenuPermissionSnapshot query(MenuPermissionQuery query);

    MenuPermissionSnapshot replace(String idempotencyKey, MenuPermissionReplaceCommand command);
}
