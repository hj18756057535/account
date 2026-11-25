package com.hypers.account.auth;

import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.model.AccountUser;
import com.hypers.account.mapper.AccountUserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Account Center 登录认证服务。
 *
 * <p>按账号读取用户并用 BCrypt 校验密码；失败统一抛 {@link BusinessException}，
 * 由 web 层按请求语言翻译，不在这里拼接提示文本。
 */
public class AccountLoginService {

    private final AccountUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountLoginService(AccountUserMapper userMapper) {
        this.userMapper = userMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 验证账号密码，成功返回 AccountSessionUser。
     */
    public AccountSessionUser authenticate(String account, String password) {
        AccountUser user = userMapper.selectByAccount(account);
        if (user == null) {
            throw credentialsInvalid();
        }
        if ("disabled".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw credentialsInvalid();
        }
        return new AccountSessionUser(user.getId(), user.getAccount(), user.getName());
    }

    /** 对明文密码进行 BCrypt 哈希。 */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    private BusinessException credentialsInvalid() {
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
}
