package com.hypers.account.web;

import com.hypers.account.app.AccountApplication;
import com.hypers.account.app.AccountDirectoryService;
import com.hypers.account.app.AccountUser;
import com.hypers.account.app.RegisterApplicationCommand;
import com.hypers.account.app.SaveUserCommand;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AccountDirectoryController {

    private final AccountDirectoryService directoryService;

    public AccountDirectoryController(AccountDirectoryService directoryService) {
        this.directoryService = directoryService;
    }

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

    @PostMapping("/users")
    public AccountUser createUser(@Valid @RequestBody SaveUserRequest request) {
        return directoryService.createUser(new SaveUserCommand(
                request.getAccount(),
                request.getEmail(),
                request.getName(),
                request.getPhone()));
    }

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
        @NotBlank
        private String defaultTenantCode;

        public String getAppCode() {
            return appCode;
        }

        public void setAppCode(String appCode) {
            this.appCode = appCode;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEntryUrl() {
            return entryUrl;
        }

        public void setEntryUrl(String entryUrl) {
            this.entryUrl = entryUrl;
        }

        public String getSsoCallbackUrl() {
            return ssoCallbackUrl;
        }

        public void setSsoCallbackUrl(String ssoCallbackUrl) {
            this.ssoCallbackUrl = ssoCallbackUrl;
        }

        public String getPermissionIframeUrl() {
            return permissionIframeUrl;
        }

        public void setPermissionIframeUrl(String permissionIframeUrl) {
            this.permissionIframeUrl = permissionIframeUrl;
        }

        public String getNotifyBaseUrl() {
            return notifyBaseUrl;
        }

        public void setNotifyBaseUrl(String notifyBaseUrl) {
            this.notifyBaseUrl = notifyBaseUrl;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getDefaultTenantCode() {
            return defaultTenantCode;
        }

        public void setDefaultTenantCode(String defaultTenantCode) {
            this.defaultTenantCode = defaultTenantCode;
        }
    }

    public static class SaveUserRequest {

        @NotBlank
        private String account;
        @NotBlank
        private String email;
        @NotBlank
        private String name;
        @NotBlank
        private String phone;

        public String getAccount() {
            return account;
        }

        public void setAccount(String account) {
            this.account = account;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }
}
