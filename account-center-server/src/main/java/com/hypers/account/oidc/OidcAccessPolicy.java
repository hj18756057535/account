package com.hypers.account.oidc;

import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.service.ApplicationManagementService;
import com.hypers.account.app.service.UserApplicationAccessService;
import com.hypers.account.app.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

/**
 * OIDC 准入判断：应用启用、用户启用，并且存在有效的授权/准入记录。
 *
 * <p>只依赖应用、用户和授权的读能力，不注入写模型，避免 OIDC 协议组件获得修改账号数据的能力。
 */
@RequiredArgsConstructor
public class OidcAccessPolicy {
    private final UserManagementService userService;
    private final ApplicationManagementService applicationService;
    private final UserApplicationAccessService accessService;

    public boolean allows(String userId, String appCode) {
        try {
            var application = applicationService.getApplication(appCode);
            boolean provisionedUsers = application.hasProviderConfiguration();
            return "enabled".equals(userService.getUser(userId).getStatus())
                    && "enabled".equals(application.getStatus())
                    && (provisionedUsers ? accessService.isAuthorized(userId, appCode)
                        : "enabled".equals(accessService.getUserApplicationAccess(userId, appCode).getDesiredStatus()));
        } catch (BusinessException exception) {
            return false;
        }
    }

    public void require(String userId, String appCode, String errorCode) {
        if (!allows(userId, appCode)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(errorCode));
        }
    }
}
