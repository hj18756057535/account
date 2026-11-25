package com.hypers.account.app.service;

import com.hypers.account.app.command.SaveUserCommand;
import com.hypers.account.app.command.UserPageQuery;
import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.model.AccountUser;
import com.hypers.account.common.PageResult;
import com.hypers.account.mapper.AccountUserMapper;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 用户管理应用服务。
 *
 * <p>承载管理端与系统任务对用户的读写编排：建档、版本化更新、查重与分页查询都直接使用
 * {@link AccountUserMapper}，异常统一抛带国际化消息键的 {@link BusinessException}。
 * 授权关系不在此处处理，见 {@link UserApplicationAccessService}。
 */
@Service
@RequiredArgsConstructor
public class UserManagementService {

    private final AccountUserMapper userMapper;

    /** 返回入参中已存在的账号，用于导入前批量查重。 */
    public List<String> findExistingAccounts(List<String> accounts) {
        return accounts.isEmpty() ? Collections.emptyList() : userMapper.selectExistingAccounts(accounts);
    }

    /** 按账号或用户 ID 批量查询，两者都为空时返回空集合。 */
    public List<AccountUser> findByIdsOrAccounts(List<String> accounts, List<String> ids) {
        return accounts.isEmpty() && ids.isEmpty()
                ? Collections.emptyList()
                : userMapper.selectByAccountsOrIds(accounts, ids);
    }

    /** 批量创建用户，供目录导入等系统任务使用。 */
    public List<AccountUser> createUsers(List<SaveUserCommand> commands, String operatorId) {
        return createUsers(commands, Set.of(), operatorId);
    }

    /**
     * 批量创建用户。
     *
     * @param disabledAccounts 需要直接置为禁用状态的账号，通常来自导入数据
     */
    public List<AccountUser> createUsers(List<SaveUserCommand> commands,
                                         Set<String> disabledAccounts,
                                         String operatorId) {
        if (commands.isEmpty()) return Collections.emptyList();
        List<AccountUser> users = commands.stream().map(command -> {
            AccountUser user = newUser(command, operatorId);
            if (disabledAccounts.contains(command.getAccount())) {
                user.setStatus("disabled");
            }
            return user;
        }).collect(Collectors.toList());
        try {
            userMapper.insertBatch(users);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        return users;
    }

    /** 创建用户，不带操作人；管理端写入请使用 {@link #createManagedUser}。 */
    public AccountUser createUser(SaveUserCommand command) {
        return createManagedUser(command, null);
    }

    public AccountUser createManagedUser(SaveUserCommand command, String operatorId) {
        AccountUser user = newUser(command, operatorId);
        try {
            userMapper.insert(user);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        return require(user.getId());
    }

    public AccountUser getUser(String userId) {
        return require(userId);
    }

    /** 按账号查询用户，不存在时返回 null，供登录等“未命中即失败”的场景使用。 */
    public AccountUser findByAccount(String account) {
        return userMapper.selectByAccount(account);
    }

    public List<AccountUser> findUsers(String keyword, String status) {
        return userMapper.selectByKeyword(keyword, status);
    }

    public PageResult<AccountUser> findUsersPage(UserPageQuery query) {
        long total = userMapper.count(query);
        List<AccountUser> items = total == 0 ? Collections.emptyList() : userMapper.selectPage(query);
        return new PageResult<>(items, query.getPage(), query.getSize(), total);
    }

    /** 管理端按显式版本更新用户资料。 */
    public AccountUser updateManagedUser(String userId,
                                         SaveUserCommand command,
                                         long expectedVersion,
                                         String operatorId) {
        AccountUser user = require(userId);
        user.setAccount(command.getAccount());
        user.setEmail(command.getEmail());
        user.setName(command.getName());
        user.setPhone(command.getPhone());
        user.setUpdatedBy(operatorId);
        try {
            if (userMapper.updateVersioned(user, expectedVersion) == 0) {
                throw new BusinessException(ErrorCode.USER_CONFLICT);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.ACCOUNT_ALREADY_EXISTS);
        }
        return require(userId);
    }

    /** 管理端按显式版本调整用户启停状态。 */
    public AccountUser changeManagedUserStatus(String userId,
                                               String status,
                                               long expectedVersion,
                                               String operatorId) {
        require(userId);
        if (userMapper.updateStatusVersioned(userId, status, expectedVersion, operatorId) == 0) {
            throw new BusinessException(ErrorCode.USER_CONFLICT);
        }
        return require(userId);
    }

    /** 写入加密后的密码，仅由登录/凭据流程调用。 */
    public void updatePassword(String userId, String encodedPassword) {
        userMapper.updatePassword(userId, encodedPassword);
    }

    private AccountUser require(String userId) {
        AccountUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    private AccountUser newUser(SaveUserCommand command, String operatorId) {
        AccountUser user = new AccountUser(
                UUID.randomUUID().toString().replace("-", ""),
                command.getAccount(),
                command.getEmail(),
                command.getName(),
                command.getPhone());
        user.setCreatedBy(operatorId);
        return user;
    }
}
