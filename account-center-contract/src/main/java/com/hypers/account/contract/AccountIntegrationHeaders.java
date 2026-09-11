package com.hypers.account.contract;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AccountIntegrationHeaders {

    public static final String APP_CODE = "X-Account-App-Code";
    public static final String TIMESTAMP = "X-Account-Timestamp";
    public static final String NONCE = "X-Account-Nonce";
    public static final String SIGNATURE = "X-Account-Signature";
    public static final String PROTOCOL_VERSION = "X-Account-Protocol-Version";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
}
