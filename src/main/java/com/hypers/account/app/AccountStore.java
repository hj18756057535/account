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
}
