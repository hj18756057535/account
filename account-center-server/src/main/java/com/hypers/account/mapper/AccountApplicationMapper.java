package com.hypers.account.mapper;

import com.hypers.account.app.AccountApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountApplicationMapper {

    void insert(@Param("app") AccountApplication app);

    void update(@Param("app") AccountApplication app);

    AccountApplication selectByAppCode(@Param("appCode") String appCode);

    List<AccountApplication> selectByKeyword(@Param("keyword") String keyword, @Param("status") String status);

    void updateStatus(@Param("appCode") String appCode, @Param("status") String status);

    void updateSecret(@Param("appCode") String appCode, @Param("secret") String secret, @Param("secretVersion") int secretVersion);
}
