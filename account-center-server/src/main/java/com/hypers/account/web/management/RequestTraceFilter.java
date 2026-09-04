package com.hypers.account.web.management;

import com.hypers.account.audit.AuditLogService;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.MDC;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_ATTRIBUTE = RequestTraceFilter.class.getName() + ".traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return !"/api/session".equals(requestUri)
                && !requestUri.startsWith("/api/user-imports/")
                && !"/api/users".equals(requestUri)
                && !requestUri.startsWith("/api/users/")
                && !"/api/applications".equals(requestUri)
                && !requestUri.startsWith("/api/applications/")
                && !"/api/audit-events".equals(requestUri)
                && !requestUri.startsWith("/api/audit-events/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader("Cache-Control", "no-store");
        String previousTrace = MDC.get(AuditLogService.TRACE_CONTEXT_KEY);
        MDC.put(AuditLogService.TRACE_CONTEXT_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousTrace == null) MDC.remove(AuditLogService.TRACE_CONTEXT_KEY);
            else MDC.put(AuditLogService.TRACE_CONTEXT_KEY, previousTrace);
        }
    }

    public static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TRACE_ID_ATTRIBUTE);
        return value == null ? "" : value.toString();
    }
}
