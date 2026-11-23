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

}
