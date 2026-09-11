package com.hypers.account.contract.application;

import java.util.LinkedHashMap;
import java.util.Map;
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
public class MenuPermissionNode {

    private String code;
    private String parentCode;
    private String nodeType;
    private String defaultName;
    @Builder.Default
    private Map<String, String> localizedNames = new LinkedHashMap<>();
    private boolean assignable;
    private int sort;
}
