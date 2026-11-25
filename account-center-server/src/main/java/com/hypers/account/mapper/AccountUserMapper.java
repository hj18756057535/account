package com.hypers.account.mapper;

import com.hypers.account.app.model.AccountUser;
import com.hypers.account.app.command.UserPageQuery;
import com.hypers.account.app.model.OverviewCounts;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccountUserMapper {

    OverviewCounts selectOverviewCounts(
            @Param("disabledBefore") java.time.Instant disabledBefore);

    List<String> selectExistingAccounts(@Param("accounts") List<String> accounts);

    List<AccountUser> selectByAccountsOrIds(@Param("accounts") List<String> accounts, @Param("ids") List<String> ids);

    void insertBatch(@Param("users") List<AccountUser> users);

    void insert(@Param("user") AccountUser user);

    int update(@Param("user") AccountUser user);

    int updateVersioned(@Param("user") AccountUser user, @Param("expectedVersion") long expectedVersion);

    AccountUser selectById(@Param("id") String id);

    AccountUser selectByAccount(@Param("account") String account);

    List<AccountUser> selectByKeyword(@Param("keyword") String keyword, @Param("status") String status);

    List<AccountUser> selectPage(@Param("query") UserPageQuery query);

    long count(@Param("query") UserPageQuery query);

    void updateStatus(@Param("id") String id, @Param("status") String status);

    int updateStatusVersioned(@Param("id") String id,
                              @Param("status") String status,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("updatedBy") String updatedBy);

    void updatePassword(@Param("id") String id, @Param("password") String password);
}
