package com.hypers.account.oidc;

import com.hypers.account.app.AccountStore;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.web.AuthController;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.filter.OncePerRequestFilter;

/** 每次用现有可信会话重建标准身份，禁用用户不能沿用旧 SecurityContext。 */
@RequiredArgsConstructor
final class OidcSessionFilter extends OncePerRequestFilter {
    private final AccountStore store;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var context = SecurityContextHolder.createEmptyContext();
        var session = request.getSession(false);
        var identity = session == null ? null
                : (AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY);
        if (identity != null) {
            try {
                if ("enabled".equals(store.requireUser(identity.getUserId()).getStatus())) {
                    var principal = User.withUsername(identity.getUserId()).password("")
                            .authorities("ACCOUNT_USER").build();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, principal.getAuthorities()));
                } else {
                    session.removeAttribute(AuthController.SESSION_USER_KEY);
                }
            } catch (IllegalArgumentException exception) {
                session.removeAttribute(AuthController.SESSION_USER_KEY);
            }
        }
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
