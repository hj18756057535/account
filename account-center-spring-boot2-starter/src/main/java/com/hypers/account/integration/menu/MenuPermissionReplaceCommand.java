package com.hypers.account.integration.menu;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class MenuPermissionReplaceCommand extends MenuPermissionQuery {

    private String expectedCatalogRevision;
    private String expectedPermissionRevision;
    private List<String> selectedCodes = new ArrayList<String>();
    /** 未提供时保留角色关联；空集合清空可管理范围内的普通角色关联。 */
    private List<String> selectedRoleIds;

}
