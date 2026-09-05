package com.hypers.account.mapper;

import com.hypers.account.app.ApplicationAccess;
import com.hypers.account.app.ApplicationSyncDelivery;
import com.hypers.account.app.ApplicationUserState;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ApplicationSyncMapper {
    Boolean lockApplication(@Param("appCode") String appCode);
    void enroll(@Param("appCode") String appCode);
    ApplicationAccess lockAccess(@Param("userId") String userId, @Param("appCode") String appCode);
    ApplicationSyncDelivery find(@Param("id") String id);
    ApplicationSyncDelivery lock(@Param("id") String id);
    ApplicationSyncDelivery latest(@Param("userId") String userId, @Param("appCode") String appCode, @Param("version") long version);
    void save(@Param("delivery") ApplicationSyncDelivery delivery);
    List<String> due(@Param("now") Instant now);
    List<String> expired(@Param("cutoff") Instant cutoff);
    void supersede(@Param("userId") String userId, @Param("appCode") String appCode, @Param("version") long version, @Param("now") Instant now);
    ApplicationUserState state(@Param("userId") String userId, @Param("appCode") String appCode);
    void insertState(@Param("state") ApplicationUserState state);
    void updateState(@Param("state") ApplicationUserState state);
}
