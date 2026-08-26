package com.hypers.account.app;

import java.util.List;

/**
 * 数据访问契约接口。
 * 封装所有持久化操作，Service 层只依赖此接口，不直接调用 Mapper。
 * 生产使用 MyBatisAccountStore，测试使用 InMemoryAccountStore。
 */
public interface AccountStore {

    AccountUser saveNewUser(SaveUserCommand command);

    AccountUser saveNewUser(SaveUserCommand command, String operatorId);

    AccountUser updateUser(String userId, SaveUserCommand command);

    AccountUser updateUser(String userId, SaveUserCommand command, long expectedVersion, String operatorId);

    AccountApplication saveApplication(RegisterApplicationCommand command);

    AccountUser requireUser(String userId);

    AccountApplication requireApplication(String appCode);

    void authorize(String userId, String appCode);

    void deauthorize(String userId, String appCode);

    List<AccountApplication> findAuthorizedApplications(String userId);

    boolean isAuthorized(String userId, String appCode);

    List<AccountUser> findUsers(String keyword, String status);

    PageResult<AccountUser> findUsersPage(UserPageQuery query);

    List<AccountApplication> findApplications(String keyword, String status);

    void updateUserStatus(String userId, String status);

    AccountUser updateUserStatus(String userId, String status, long expectedVersion, String operatorId);

    void updateApplicationStatus(String appCode, String status);

    void rotateApplicationSecret(String appCode, String newSecret, int newVersion);

    AccountUser findUserByAccount(String account);

    void setUserPassword(String userId, String encodedPassword);
}
