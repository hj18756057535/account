package com.hypers.account.web.management;

import com.hypers.account.auth.AccountLoginService;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.auth.AdminAuthorizationService;
import com.hypers.account.web.AuthController;
import java.util.Collections;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
@ConditionalOnProperty(name = "account.console-api.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ManagementSessionController {

    private final AccountLoginService loginService;
    private final AdminAuthorizationService authorizationService;
    private final CsrfTokenManager csrfTokenManager;

    @GetMapping
    public SessionResponse currentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        AccountSessionUser sessionUser = (AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY);
        return toResponse(sessionUser, csrfTokenManager.ensureToken(session));
    }

    @PostMapping
    public SessionResponse login(@Valid @RequestBody LoginRequest loginRequest,
                                 HttpServletRequest request) {
        AccountSessionUser user;
        try {
            user = loginService.authenticate(loginRequest.getAccount(), loginRequest.getPassword());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "INVALID_CREDENTIALS", "账号或密码错误");
        }
        List<String> roles = authorizationService.findRoles(user.getUserId());
        if (!roles.contains(AdminAuthorizationService.ACCOUNT_ADMIN)
                && !roles.contains(AdminAuthorizationService.ACCOUNT_AUDITOR)) {
            request.getSession(false).invalidate();
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "MANAGEMENT_ACCESS_DENIED", "当前账号没有管理端访问权限");
        }
        request.changeSessionId();
        HttpSession session = request.getSession(false);
        session.setAttribute(AuthController.SESSION_USER_KEY, user);
        return toResponse(user, csrfTokenManager.ensureToken(session));
    }

    @DeleteMapping
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    private SessionResponse toResponse(AccountSessionUser sessionUser, String csrfToken) {
        if (sessionUser == null) {
            return new SessionResponse(false, null, Collections.emptyList(), Collections.emptyList(), csrfToken);
        }
        List<String> roles = authorizationService.findRoles(sessionUser.getUserId());
        return new SessionResponse(
                true,
                new SessionUser(sessionUser.getUserId(), sessionUser.getAccount(), sessionUser.getName()),
                roles,
                authorizationService.findCapabilities(roles),
                csrfToken);
    }

    @Getter
    @Setter
    public static class LoginRequest {

        @NotBlank(message = "请输入账号")
        @Size(max = 128, message = "账号长度不能超过 128 个字符")
        private String account;
        @NotBlank(message = "请输入密码")
        @Size(max = 256, message = "密码长度不能超过 256 个字符")
        private String password;

    }

    @Value
    public static class SessionResponse {

        boolean authenticated;
        SessionUser user;
        List<String> roles;
        List<String> capabilities;
        String csrfToken;
    }

    @Value
    public static class SessionUser {

        String id;
        String account;
        String name;
    }
}
