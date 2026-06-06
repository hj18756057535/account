package com.hypers.account.mapper;

import com.hypers.account.app.AccountUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountUserMapper {

    void insert(@Param("user") AccountUser user);

    void update(@Param("user") AccountUser user);

    AccountUser selectById(@Param("id") String id);

    AccountUser selectByAccount(@Param("account") String account);

    List<AccountUser> selectByKeyword(@Param("keyword") String keyword, @Param("status") String status);

    void updateStatus(@Param("id") String id, @Param("status") String status);
}
