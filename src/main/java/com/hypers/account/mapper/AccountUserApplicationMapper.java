package com.hypers.account.mapper;

import com.hypers.account.app.AccountApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountUserApplicationMapper {

    void insert(@Param("userId") String userId, @Param("appCode") String appCode);

    void delete(@Param("userId") String userId, @Param("appCode") String appCode);

    int countByUserAndApp(@Param("userId") String userId, @Param("appCode") String appCode);

    List<AccountApplication> selectAuthorizedApplications(@Param("userId") String userId);
}
