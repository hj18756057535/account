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
}
