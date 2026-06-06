package com.hypers.account.app;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class JdbcAccountStore implements AccountStore {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<AccountUser> userMapper = (rs, rowNum) -> new AccountUser(
            rs.getString("id"),
            rs.getString("account"),
            rs.getString("email"),
            rs.getString("name"),
            rs.getString("phone"));
    private final RowMapper<AccountApplication> applicationMapper = (rs, rowNum) -> new AccountApplication(
            rs.getString("app_code"),
            rs.getString("name"),
            rs.getString("entry_url"),
            rs.getString("sso_callback_url"),
            rs.getString("permission_iframe_url"),
            rs.getString("notify_base_url"),
            rs.getString("secret"),
            rs.getString("default_tenant_code"));

    public JdbcAccountStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AccountUser saveNewUser(SaveUserCommand command) {
        AccountUser user = new AccountUser(
                UUID.randomUUID().toString(),
                command.getAccount(),
                command.getEmail(),
                command.getName(),
                command.getPhone());
        jdbcTemplate.update(
                "insert into account_users (id, account, email, name, phone) values (?, ?, ?, ?, ?)",
                user.getId(),
                user.getAccount(),
                user.getEmail(),
                user.getName(),
                user.getPhone());
        return user;
    }

    @Override
    public AccountUser updateUser(String userId, SaveUserCommand command) {
        int updated = jdbcTemplate.update(
                "update account_users set account = ?, email = ?, name = ?, phone = ? where id = ?",
                command.getAccount(),
                command.getEmail(),
                command.getName(),
                command.getPhone(),
                userId);
        if (updated == 0) {
            throw new IllegalArgumentException("user not found");
        }
        return requireUser(userId);
    }

    @Override
    public AccountApplication saveApplication(RegisterApplicationCommand command) {
        String defaultTenantCode = Optional.ofNullable(command.getDefaultTenantCode())
                .filter(value -> !value.trim().isEmpty())
                .orElse("default");
        int updated = jdbcTemplate.update(
                "update account_applications set name = ?, entry_url = ?, sso_callback_url = ?, "
                        + "permission_iframe_url = ?, notify_base_url = ?, secret = ?, default_tenant_code = ? "
                        + "where app_code = ?",
                command.getName(),
                command.getEntryUrl(),
                command.getSsoCallbackUrl(),
                command.getPermissionIframeUrl(),
                command.getNotifyBaseUrl(),
                command.getSecret(),
                defaultTenantCode,
                command.getAppCode());
        if (updated == 0) {
            jdbcTemplate.update(
                    "insert into account_applications "
                            + "(app_code, name, entry_url, sso_callback_url, permission_iframe_url, notify_base_url, secret, default_tenant_code) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                    command.getAppCode(),
                    command.getName(),
                    command.getEntryUrl(),
                    command.getSsoCallbackUrl(),
                    command.getPermissionIframeUrl(),
                    command.getNotifyBaseUrl(),
                    command.getSecret(),
                    defaultTenantCode);
        }
        return requireApplication(command.getAppCode());
    }

    @Override
    public AccountUser requireUser(String userId) {
        try {
            return jdbcTemplate.queryForObject(
                    "select id, account, email, name, phone from account_users where id = ?",
                    userMapper,
                    userId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("user not found", e);
        }
    }

    @Override
    public AccountApplication requireApplication(String appCode) {
        try {
            return jdbcTemplate.queryForObject(
                    "select app_code, name, entry_url, sso_callback_url, permission_iframe_url, notify_base_url, secret, default_tenant_code "
                            + "from account_applications where app_code = ?",
                    applicationMapper,
                    appCode);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("application not found", e);
        }
    }

    @Override
    public void authorize(String userId, String appCode) {
        if (!isAuthorized(userId, appCode)) {
            jdbcTemplate.update(
                    "insert into account_user_applications (user_id, app_code) values (?, ?)",
                    userId,
                    appCode);
        }
    }

    @Override
    public void deauthorize(String userId, String appCode) {
        jdbcTemplate.update(
                "delete from account_user_applications where user_id = ? and app_code = ?",
                userId,
                appCode);
    }

    @Override
    public List<AccountApplication> findAuthorizedApplications(String userId) {
        return jdbcTemplate.query(
                "select a.app_code, a.name, a.entry_url, a.sso_callback_url, a.permission_iframe_url, "
                        + "a.notify_base_url, a.secret, a.default_tenant_code "
                        + "from account_applications a "
                        + "join account_user_applications ua on ua.app_code = a.app_code "
                        + "where ua.user_id = ? order by a.app_code",
                applicationMapper,
                userId);
    }

    @Override
    public boolean isAuthorized(String userId, String appCode) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from account_user_applications where user_id = ? and app_code = ?",
                Integer.class,
                userId,
                appCode);
        return count != null && count > 0;
    }
}
