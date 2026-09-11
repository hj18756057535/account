package com.hypers.account.integration.menu;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AccountIntegrationErrorResponse {

    private String code;
    private String message;
    private String traceId;
    private List<String> fieldErrors = new ArrayList<String>();

}
