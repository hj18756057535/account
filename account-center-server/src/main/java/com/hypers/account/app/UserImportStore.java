package com.hypers.account.app;

import java.time.Instant;

public interface UserImportStore {
    void insert(UserImportBatch batch);
    UserImportBatch find(String id, String operatorId, boolean lock);
    void complete(UserImportBatch batch);
    int deleteExpired(Instant now);
}
