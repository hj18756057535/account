package com.hypers.account.app;

import com.hypers.account.mapper.ApplicationSyncMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MyBatisApplicationSyncStore implements ApplicationSyncStore {
    private final ApplicationSyncMapper mapper;
    @Override
    public Boolean lockApplication(String appCode) { return mapper.lockApplication(appCode); }
    @Override
    public void enroll(String appCode) { mapper.enroll(appCode); }
    @Override
    public ApplicationAccess lockAccess(String userId, String appCode) { return mapper.lockAccess(userId, appCode); }
    @Override
    public ApplicationSyncDelivery find(String id) { return mapper.find(id); }
    @Override
    public ApplicationSyncDelivery lock(String id) { return mapper.lock(id); }
    @Override
    public ApplicationSyncDelivery latest(String userId, String appCode, long version) { return mapper.latest(userId, appCode, version); }
    @Override
    public void save(ApplicationSyncDelivery delivery) { mapper.save(delivery); }
    @Override
    public List<String> due(Instant now) { return mapper.due(now); }
    @Override
    public List<String> expired(Instant cutoff) { return mapper.expired(cutoff); }
    @Override
    public void supersede(String userId, String appCode, long version, Instant now) { mapper.supersede(userId, appCode, version, now); }
    @Override
    public ApplicationUserState state(String userId, String appCode) { return mapper.state(userId, appCode); }
    @Override
    public void insertState(ApplicationUserState state) { mapper.insertState(state); }
    @Override
    public void updateState(ApplicationUserState state) { mapper.updateState(state); }
}
