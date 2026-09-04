package com.hypers.account.mapper;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.SaveApplicationCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountApplicationMapper {

    void insert(@Param("app") AccountApplication app);

    void update(@Param("app") AccountApplication app);

    int updateManaged(@Param("command") SaveApplicationCommand command,
                      @Param("operatorId") String operatorId);

    AccountApplication selectByAppCode(@Param("appCode") String appCode);

    List<AccountApplication> selectByKeyword(@Param("keyword") String keyword, @Param("status") String status);

    void updateStatus(@Param("appCode") String appCode, @Param("status") String status);

    int updateStatusVersioned(@Param("appCode") String appCode,
                              @Param("status") String status,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("operatorId") String operatorId);

    void updateSecret(@Param("appCode") String appCode, @Param("secret") String secret, @Param("secretVersion") int secretVersion);

    int rotateSecretVersioned(@Param("appCode") String appCode,
                              @Param("secret") String secret,
                              @Param("secretVersion") int secretVersion,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("operatorId") String operatorId);

    int revokeSecretVersioned(@Param("appCode") String appCode,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("operatorId") String operatorId);
}
