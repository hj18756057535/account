package com.hypers.account.app.service;

import com.hypers.account.app.model.AccountApplication;
import com.hypers.account.contract.application.MenuPermissionQuery;
import com.hypers.account.contract.application.MenuPermissionReplaceCommand;
import com.hypers.account.contract.application.MenuPermissionSnapshot;

/** 下游应用的菜单权限协议客户端。 */
public interface ApplicationMenuPermissionClient {

    MenuPermissionSnapshot query(AccountApplication application, MenuPermissionQuery query);

    MenuPermissionSnapshot replace(AccountApplication application,
                                   String idempotencyKey,
                                   MenuPermissionReplaceCommand command);
}
