package com.hypers.account.auth;

import com.hypers.account.app.AccountUser;
import com.hypers.account.app.AccountStore;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Account Center 登录认证服务。
 * 验证用户凭据，使用 BCrypt 校验密码。
 */
public class AccountLoginService {

    private final AccountStore store;
    private final PasswordEncoder passwordEncoder;

    public AccountLoginService(AccountStore store) {
        this.store = store;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 验证账号密码，成功返回 AccountSessionUser。
     * 失败抛 IllegalArgumentException。
     */
    public AccountSessionUser authenticate(String account, String password) {
        AccountUser user = store.findUserByAccount(account);
        if (user == null) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if ("disabled".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已禁用");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new IllegalArgumentException("该账号未设置密码，请联系管理员");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        return new AccountSessionUser(user.getId(), user.getAccount(), user.getName());
    }

    /** 对明文密码进行 BCrypt 哈希 */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
