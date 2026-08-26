package com.hypers.account.web;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.RegisterApplicationCommand;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountDirectoryController {

    private final AccountDirectoryService directoryService;

    @GetMapping("/users/{userId}/applications")
    public List<AccountApplication> getUserApplications(@PathVariable String userId) {
        return directoryService.getUserAuthorizedApplications(userId);
    }

    // ========== 应用管理 ==========

    @PostMapping("/applications")
    public AccountApplication registerApplication(@Valid @RequestBody RegisterApplicationRequest request) {
        return directoryService.registerApplication(new RegisterApplicationCommand(
                request.getAppCode(),
                request.getName(),
                request.getEntryUrl(),
                request.getSsoCallbackUrl(),
                request.getPermissionIframeUrl(),
                request.getNotifyBaseUrl(),
                request.getSecret(),
                request.getDefaultTenantCode()));
    }

    @GetMapping("/applications")
    public List<AccountApplication> findApplications(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return directoryService.findApplications(keyword, status);
    }

    @GetMapping("/applications/{appCode}")
    public AccountApplication getApplication(@PathVariable String appCode) {
        return directoryService.getApplication(appCode);
    }

    @PutMapping("/applications/{appCode}")
    public AccountApplication updateApplication(@PathVariable String appCode,
                                                 @Valid @RequestBody RegisterApplicationRequest request) {
        return directoryService.updateApplication(appCode, new RegisterApplicationCommand(
                request.getAppCode(),
                request.getName(),
                request.getEntryUrl(),
                request.getSsoCallbackUrl(),
                request.getPermissionIframeUrl(),
                request.getNotifyBaseUrl(),
                request.getSecret(),
                request.getDefaultTenantCode()));
    }

    @PostMapping("/applications/{appCode}/enable")
    public ResponseEntity<Void> enableApplication(@PathVariable String appCode) {
        directoryService.enableApplication(appCode);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/applications/{appCode}/disable")
    public ResponseEntity<Void> disableApplication(@PathVariable String appCode) {
        directoryService.disableApplication(appCode);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/applications/{appCode}/secret/rotate")
    public RotateSecretResponse rotateSecret(@PathVariable String appCode) {
        String newSecret = directoryService.rotateApplicationSecret(appCode);
        return new RotateSecretResponse(newSecret);
    }

    // ========== 授权管理 ==========

    @PostMapping("/users/{userId}/applications/{appCode}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String userId, @PathVariable String appCode) {
        directoryService.authorize(userId, appCode);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/applications/{appCode}/deauthorize")
    public ResponseEntity<Void> deauthorize(@PathVariable String userId, @PathVariable String appCode) {
        directoryService.deauthorize(userId, appCode);
        return ResponseEntity.noContent().build();
    }

    // ========== Request/Response DTOs ==========

    @Getter
    @Setter
    public static class RegisterApplicationRequest {

        @NotBlank
        private String appCode;
        @NotBlank
        private String name;
        @NotBlank
        private String entryUrl;
        @NotBlank
        private String ssoCallbackUrl;
        @NotBlank
        private String permissionIframeUrl;
        @NotBlank
        private String notifyBaseUrl;
        @NotBlank
        private String secret;
        private String defaultTenantCode;

    }

    @Value
    public static class RotateSecretResponse {

        String newSecret;
    }
}
