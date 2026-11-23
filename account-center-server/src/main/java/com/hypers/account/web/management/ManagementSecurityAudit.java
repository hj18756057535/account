package com.hypers.account.web.management;

import com.hypers.account.audit.AuditLogService;
import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.web.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 只接收内部事件名，不采集请求参数、登录账号或凭据。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ManagementSecurityAudit {
    private final AuditLogService audit;

    public void denied(HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute(getClass().getName()))) return;
        request.setAttribute(getClass().getName(), true);
        var session = request.getSession(false);
        var user = session == null ? null
                : (AccountSessionUser) session.getAttribute(AuthController.SESSION_USER_KEY);
        record(user == null ? null : user.getUserId(), "MANAGEMENT_ACCESS_DENIED", false);
    }

    public void record(String userId, String operation, boolean success) {
        try {
            audit.securityEvent(userId, operation, success);
        } catch (RuntimeException exception) {
            // 审计故障不能把拒绝变成成功，也不打印可能携带数据的异常消息。
            log.error("Management security audit unavailable; traceId={}",
                    org.slf4j.MDC.get(AuditLogService.TRACE_CONTEXT_KEY));
        }
    }
}
