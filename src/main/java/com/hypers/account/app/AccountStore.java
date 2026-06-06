package com.hypers.account.app;

import java.util.List;

public interface AccountStore {

    AccountUser saveNewUser(SaveUserCommand command);

    AccountUser updateUser(String userId, SaveUserCommand command);

    AccountApplication saveApplication(RegisterApplicationCommand command);

    AccountUser requireUser(String userId);

    AccountApplication requireApplication(String appCode);

    void authorize(String userId, String appCode);

    void deauthorize(String userId, String appCode);

    List<AccountApplication> findAuthorizedApplications(String userId);

    boolean isAuthorized(String userId, String appCode);

    List<AccountUser> findUsers(String keyword, String status);

    List<AccountApplication> findApplications(String keyword, String status);

    void updateUserStatus(String userId, String status);

    void updateApplicationStatus(String appCode, String status);

    void rotateApplicationSecret(String appCode, String newSecret, int newVersion);

    AccountUser findUserByAccount(String account);
}
