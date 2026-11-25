package com.hypers.account.integration.menu;

import com.hypers.account.integration.directory.AccountDirectoryRole;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuPermissionSnapshot {

    private String catalogRevision;
    private String permissionRevision;
    private String assignmentMode;
    private List<MenuPermissionNode> nodes = new ArrayList<MenuPermissionNode>();
    private List<String> selectedCodes = new ArrayList<String>();
    private List<String> inheritedCodes = new ArrayList<String>();
    /** 应用提供的已有角色，未提供时不展示角色关联入口。 */
    private List<AccountDirectoryRole> roles;
    /** 当前用户关联的可管理角色 ID，始终使用字符串。 */
    private List<String> selectedRoleIds;

}
