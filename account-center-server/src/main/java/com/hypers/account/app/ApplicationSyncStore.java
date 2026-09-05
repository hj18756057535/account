package com.hypers.account.app;

import java.time.Instant;
import java.util.List;

public interface ApplicationSyncStore {
    Boolean lockApplication(String appCode);
    void enroll(String appCode);
    ApplicationAccess lockAccess(String userId, String appCode);
    ApplicationSyncDelivery find(String id);
    ApplicationSyncDelivery lock(String id);
    ApplicationSyncDelivery latest(String userId, String appCode, long version);
    void save(ApplicationSyncDelivery delivery);
    List<String> due(Instant now);
    List<String> expired(Instant cutoff);
    void supersede(String userId, String appCode, long version, Instant now);
    ApplicationUserState state(String userId, String appCode);
    void insertState(ApplicationUserState state);
    void updateState(ApplicationUserState state);
}
