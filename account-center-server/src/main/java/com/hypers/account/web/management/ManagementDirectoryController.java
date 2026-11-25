package com.hypers.account.web.management;

import com.hypers.account.app.directory.ApplicationDirectoryClient;
import com.hypers.account.app.directory.ApplicationDirectorySnapshot;
import com.hypers.account.app.directory.DirectoryImportPreview;
import com.hypers.account.app.directory.DirectoryImportResult;
import com.hypers.account.app.directory.DirectoryImportService;
import com.hypers.account.app.service.ApplicationManagementService;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.web.AuthController;
import com.hypers.account.web.management.service.IdempotencyService;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 现有管理员 Session/CSRF 拦截器统一保护本控制器，不允许前端直连 CMS。 */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "account.console-api.enabled", havingValue = "true", matchIfMissing = true)
public class ManagementDirectoryController {
    private final ApplicationManagementService applicationService;
    private final ApplicationDirectoryClient client;
    private final DirectoryImportService imports;
    private final IdempotencyService idempotency;
    private final Clock clock;

    @PostMapping("/api/applications/{appCode}/connection-check")
    public ResponseEntity<ConnectionResult> check(@PathVariable String appCode) {
        client.check(applicationService.getApplication(appCode));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new ConnectionResult(true, clock.instant()));
    }

    @PostMapping("/api/applications/{appCode}/directory-imports")
    public ResponseEntity<DirectoryImportPreview> preview(@PathVariable String appCode, HttpSession session) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(imports.preview(appCode, operator(session)));
    }

    @GetMapping("/api/applications/{appCode}/directory-imports/{importId}")
    public ResponseEntity<DirectoryImportPreview> preview(@PathVariable String appCode, @PathVariable String importId,
                                                        HttpSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imports.preview(appCode, importId, operator(session)));
    }

    @PostMapping("/api/applications/{appCode}/directory-imports/{importId}/commit")
    public ResponseEntity<DirectoryImportResult> commit(@PathVariable String appCode, @PathVariable String importId,
            @RequestHeader(name = "Idempotency-Key", required = false) String key, HttpSession session) {
        String operator = operator(session);
        DirectoryImportResult result = idempotency.execute(operator, "POST",
                "/api/applications/" + appCode + "/directory-imports/" + importId + "/commit", key, importId,
                200, DirectoryImportResult.class, () -> imports.commit(appCode, importId, operator));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @GetMapping("/api/applications/{appCode}/directory")
    public ResponseEntity<ApplicationDirectorySnapshot> latest(@PathVariable String appCode) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imports.latest(appCode));
    }

    private String operator(HttpSession session) {
        return ((AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY)).getUserId();
    }

    @Value
    public static class ConnectionResult {
        boolean connected;
        Instant checkedAt;
    }
}
