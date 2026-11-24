package com.hypers.account.integration.web;

import com.hypers.account.integration.AccountProviderProtocol;
import com.hypers.account.integration.directory.AccountDirectoryHandler;
import com.hypers.account.integration.directory.AccountDirectoryPage;
import com.hypers.account.integration.directory.AccountDirectoryQuery;
import com.hypers.account.integration.menu.AccountMenuPermissionProtocol;
import com.hypers.account.integration.user.AccountUserDesiredState;
import com.hypers.account.integration.user.AccountUserSyncHandler;
import com.hypers.account.integration.user.AccountUserSyncResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 请求先经共享签名 Filter，宿主 Handler 负责数据范围和事务。 */
@RestController
@RequiredArgsConstructor
public class AccountProviderController {
    private final String appCode;
    private final AccountDirectoryHandler directory;
    private final AccountUserSyncHandler users;

    @PostMapping(AccountProviderProtocol.DIRECTORY_PATH)
    public ResponseEntity<AccountDirectoryPage> query(@RequestBody AccountDirectoryQuery query) {
        Assert.notNull(query, "Directory query is required");
        Assert.isTrue("users".equals(query.getKind()) || "roles".equals(query.getKind())
                || "organizations".equals(query.getKind()), "Invalid directory kind");
        Assert.isTrue(query.getLimit() > 0 && query.getLimit() <= 200, "Invalid directory page limit");
        Assert.isTrue(query.getAfterId() == null || query.getAfterId().matches("[0-9]{1,19}"), "Invalid cursor");
        AccountDirectoryPage result = directory.query(query);
        result.setAppCode(appCode);
        result.setKind(query.getKind());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @PutMapping(AccountProviderProtocol.USERS_PATH + "/{globalUserId}")
    public ResponseEntity<AccountUserSyncResult> apply(@PathVariable String globalUserId,
            @RequestHeader(name = AccountMenuPermissionProtocol.IDEMPOTENCY_KEY_HEADER) String key,
            @RequestBody AccountUserDesiredState command) {
        Assert.notNull(command, "Desired state is required");
        Assert.isTrue(globalUserId.matches("[A-Za-z0-9_-]{1,64}")
                && globalUserId.equals(command.getGlobalUserId()) && appCode.equals(command.getAppCode()),
                "Invalid user sync identity");
        Assert.isTrue(!key.trim().isEmpty() && key.length() <= 128, "Invalid idempotency key");
        Assert.isTrue(command.getAccount() != null && !command.getAccount().trim().isEmpty()
                && command.getAccount().length() <= 128, "Invalid account");
        Assert.isTrue(command.getSyncVersion() > 0 && command.getSyncVersion() < 9007199254740991L,
                "Invalid sync version");
        Assert.isTrue("enabled".equals(command.getDesiredStatus()) || "disabled".equals(command.getDesiredStatus()),
                "Invalid desired status");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(users.apply(key, command));
    }
}
