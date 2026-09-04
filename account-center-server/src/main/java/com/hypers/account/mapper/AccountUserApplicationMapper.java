package com.hypers.account.mapper;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.ApplicationAccess;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountUserApplicationMapper {

    void insert(@Param("userId") String userId, @Param("appCode") String appCode);

    void insertAccess(@Param("userId") String userId,
                      @Param("appCode") String appCode,
                      @Param("desiredStatus") String desiredStatus,
                      @Param("operatorId") String operatorId);

    void delete(@Param("userId") String userId, @Param("appCode") String appCode);

    int countByUserAndApp(@Param("userId") String userId, @Param("appCode") String appCode);

    List<AccountApplication> selectAuthorizedApplications(@Param("userId") String userId);

    List<ApplicationAccess> selectAccessByUser(@Param("userId") String userId);

    ApplicationAccess selectAccess(@Param("userId") String userId,
                                   @Param("appCode") String appCode);

    int updateAccessVersioned(@Param("userId") String userId,
                              @Param("appCode") String appCode,
                              @Param("desiredStatus") String desiredStatus,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("operatorId") String operatorId);
}
