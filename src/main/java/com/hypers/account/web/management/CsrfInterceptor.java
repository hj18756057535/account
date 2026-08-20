package com.hypers.account.web.management;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
public class CsrfInterceptor implements HandlerInterceptor {

    private static final Set<String> SAFE_METHODS = new HashSet<>(Arrays.asList("GET", "HEAD", "OPTIONS"));

    private final CsrfTokenManager tokenManager;
    private final ApiErrorWriter errorWriter;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (tokenManager.matches(session, request.getHeader(CsrfTokenManager.HEADER_NAME))) {
            return true;
        }
        errorWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                "CSRF_INVALID", "请求校验已失效，请刷新页面后重试");
        return false;
    }
}
