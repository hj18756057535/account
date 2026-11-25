package com.hypers.account.contract.application;

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
public class MenuPermissionReplaceCommand {

    private String protocolVersion;
    private String appCode;
    private String globalUserId;
    private String localUserId;
    private String account;
    private String locale;
    private String expectedCatalogRevision;
    private String expectedPermissionRevision;
    @Builder.Default
    private List<String> selectedCodes = new ArrayList<>();
    /** 本次用户角色关联；未提供时不修改角色，空集合清空可管理范围内的关联。 */
    private List<String> selectedRoleIds;
}
