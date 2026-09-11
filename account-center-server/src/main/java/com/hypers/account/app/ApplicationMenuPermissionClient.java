package com.hypers.account.app;

import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;

public interface ApplicationMenuPermissionClient {

    MenuPermissionSnapshot query(AccountApplication application, MenuPermissionQuery query);

    MenuPermissionSnapshot replace(AccountApplication application,
                                   String idempotencyKey,
                                   MenuPermissionReplaceCommand command);
}
