package com.hypers.account.boot2.oidc;

import java.net.URI;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AccountOidcLoginController {

    private final AccountOidcLogin login;

    @GetMapping(AccountOidcLogin.START_PATH)
    public ResponseEntity<Void> start(HttpServletRequest request, HttpServletResponse response) {
        try {
            return redirect(login.begin(request, response));
        } catch (RuntimeException exception) {
            log.warn("Account OIDC start failed ({})", exception.getClass().getSimpleName());
            return redirect(login.loginPage(false));
        }
    }

    @GetMapping(AccountOidcLogin.CALLBACK_PATH)
    public ResponseEntity<Void> callback(HttpServletRequest request, HttpServletResponse response) {
        boolean success = false;
        try {
            success = login.complete(request, response);
        } catch (OAuth2AuthenticationException exception) {
            String error = exception.getError().getErrorCode();
            log.warn("Account OIDC callback failed (error={})",
                    error != null && error.matches("[a-z][a-z0-9_]{0,63}") ? error : "oauth2_error");
        } catch (RuntimeException exception) {
            log.warn("Account OIDC callback failed ({})", exception.getClass().getSimpleName());
        }
        return redirect(login.loginPage(success));
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(location).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").build();
    }
}
