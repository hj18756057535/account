package com.hypers.account.app;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserImportBatch {
    private String id;
    private String fileHash;
    private int rowCount;
    private boolean valid;
    private String rowsJson;
    private String resultJson;
    private String status;
    private Instant expiresAt;
    private String createdBy;
    private String updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
