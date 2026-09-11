package com.hypers.account.integration.menu;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class MenuPermissionNode {

    private String code;
    private String parentCode;
    private String nodeType;
    private String defaultName;
    private Map<String, String> localizedNames = new LinkedHashMap<String, String>();
    private boolean assignable;
    private int sort;

}
