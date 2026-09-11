package com.hypers.account.integration.menu;

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

}
