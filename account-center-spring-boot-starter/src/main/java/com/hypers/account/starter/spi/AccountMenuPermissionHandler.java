package com.hypers.account.starter.spi;

import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;

public interface AccountMenuPermissionHandler {

    MenuPermissionSnapshot query(MenuPermissionQuery query);

    MenuPermissionSnapshot replace(String idempotencyKey, MenuPermissionReplaceCommand command);
}
