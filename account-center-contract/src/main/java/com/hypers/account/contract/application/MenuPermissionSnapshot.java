package com.hypers.account.contract.application;

import com.hypers.account.contract.directory.AccountDirectoryRole;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuPermissionSnapshot {

    private String catalogRevision;
    private String permissionRevision;
    private String assignmentMode;
    @Builder.Default
    private List<MenuPermissionNode> nodes = new ArrayList<>();
    @Builder.Default
    private List<String> selectedCodes = new ArrayList<>();
    @Builder.Default
    private List<String> inheritedCodes = new ArrayList<>();
    /** 应用可关联的已有角色；为空值表示该应用不提供角色关联能力。 */
    private List<AccountDirectoryRole> roles;
    /** 当前用户在可管理角色范围内的关联，ID 始终为字符串。 */
    private List<String> selectedRoleIds;
}
