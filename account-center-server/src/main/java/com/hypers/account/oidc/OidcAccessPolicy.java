package com.hypers.account.oidc;

import com.hypers.account.app.AccountStore;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

@RequiredArgsConstructor
public class OidcAccessPolicy {
    private final AccountStore store;

    public boolean allows(String userId, String appCode) {
        try {
            var application = store.requireApplication(appCode);
            boolean provisionedUsers = application.isManagedSync()
                    && Arrays.asList(application.getProtocolCapabilities().split(",")).contains("user_sync");
            return "enabled".equals(store.requireUser(userId).getStatus())
                    && "enabled".equals(application.getStatus())
                    && (provisionedUsers ? store.isAuthorized(userId, appCode)
                        : "enabled".equals(store.requireApplicationAccess(userId, appCode).getDesiredStatus()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public void require(String userId, String appCode, String errorCode) {
        if (!allows(userId, appCode)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(errorCode));
        }
    }
}
