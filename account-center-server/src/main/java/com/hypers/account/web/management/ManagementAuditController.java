package com.hypers.account.web.management;

import com.hypers.account.app.exception.BusinessException;
import com.hypers.account.app.exception.ErrorCode;
import com.hypers.account.audit.AuditLog;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.audit.AuditPageQuery;
import com.hypers.account.common.PageResult;
import com.hypers.account.web.management.service.AuditEventView;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理端审计查询接口，只做参数校验与视图转换。 */
@RestController
@RequestMapping("/api/audit-events")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "account.console-api.enabled", havingValue = "true", matchIfMissing = true)
public class ManagementAuditController {

    private final AuditLogService auditLogService;

    @GetMapping
    public PageResult<AuditEventView> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String traceId) {
        if (page < 1 || page > 100000) throw invalid("page");
        if (size < 1 || size > 100) throw invalid("size");
        AuditPageQuery query = new AuditPageQuery(page, size,
                normalize("operatorId", operatorId, 64), normalize("operationType", operationType, 64),
                normalize("targetType", targetType, 64), normalize("targetId", targetId, 128),
                normalize("traceId", traceId, 64));
        PageResult<AuditLog> result = auditLogService.findEvents(query);
        return new PageResult<>(result.getItems().stream().map(AuditEventView::from).toList(),
                result.getPage(), result.getSize(), result.getTotal());
    }

    @GetMapping("/{id}")
    public AuditEventView get(@PathVariable String id) {
        if (id.isBlank() || id.length() > 64) throw invalid("id");
        AuditLog event = auditLogService.findEvent(id);
        if (event == null) {
            throw new BusinessException(ErrorCode.AUDIT_EVENT_NOT_FOUND);
        }
        return AuditEventView.from(event);
    }

    private String normalize(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > maxLength) throw invalid(field);
        return value.trim();
    }

    private BusinessException invalid(String field) {
        return new BusinessException(ErrorCode.VALIDATION_FAILED,
                Collections.singletonMap(field, "validation.auditFilter"));
    }
}