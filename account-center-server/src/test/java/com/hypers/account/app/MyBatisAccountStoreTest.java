package com.hypers.account.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.hypers.account.mapper.AccountApplicationMapper;
import com.hypers.account.mapper.AccountUserApplicationMapper;
import com.hypers.account.mapper.AccountUserMapper;
import com.hypers.account.mapper.SyncCommandMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MyBatisAccountStoreTest {

    @Autowired
    private AccountUserMapper userMapper;

    @Autowired
    private AccountApplicationMapper applicationMapper;

    @Autowired
    private AccountUserApplicationMapper userApplicationMapper;

    @Autowired
    private SyncCommandMapper syncCommandMapper;

    @Test
    void myBatisStorePersistsUsersApplicationsAndAuthorizationsAcrossInstances() {
        AccountStore store = new MyBatisAccountStore(
                userMapper, applicationMapper, userApplicationMapper, syncCommandMapper);
        AccountApplication application = store.saveApplication(new RegisterApplicationCommand(
                "cms-ai",
                "双碳服务",
                "http://localhost:9003",
                "http://localhost:9003/account-sso/callback",
                "http://localhost:9003/account-admin/users/{externalUserId}/permissions",
                "http://localhost:9003",
                "secret",
                "default"));
        AccountUser user = store.saveNewUser(new SaveUserCommand(
                "zhangsan",
                "zhangsan@example.com",
                "张三",
                "13800000000"));

        store.authorize(user.getId(), application.getAppCode());

        assertThat(store.requireUser(user.getId()).getAccount()).isEqualTo("zhangsan");
        assertThat(store.requireApplication("cms-ai").getDefaultTenantCode()).isEqualTo("default");
        assertThat(store.isAuthorized(user.getId(), "cms-ai")).isTrue();
        assertThat(store.findAuthorizedApplications(user.getId()))
                .extracting(AccountApplication::getAppCode)
                .containsExactly("cms-ai");

        store.deauthorize(user.getId(), application.getAppCode());
        assertThat(store.isAuthorized(user.getId(), "cms-ai")).isFalse();
    }
}
