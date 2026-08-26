package com.hypers.account.app;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {

    private String id;
    private String idempotencyKey;
    private String operatorId;
    private String requestMethod;
    private String requestPath;
    private String requestHash;
    private String status;
    private Integer responseStatus;
    private String responseBody;
    private Instant createdAt;
    private Instant expiresAt;
}
