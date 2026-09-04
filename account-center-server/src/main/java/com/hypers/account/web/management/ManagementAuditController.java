package com.hypers.account.web.management;

import com.hypers.account.app.PageResult;
import com.hypers.account.audit.AuditLog;
import com.hypers.account.audit.AuditLogService;
import com.hypers.account.audit.AuditPageQuery;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-events")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "account.console-api.enabled", havingValue = "true", matchIfMissing = true)
public class ManagementAuditController {

    private final AuditLogService auditLogService;

    @GetMapping
    public PageResult<AuditEventResponse> list(
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
        return new PageResult<>(result.getItems().stream().map(AuditEventResponse::from).toList(),
                result.getPage(), result.getSize(), result.getTotal());
    }

    @GetMapping("/{id}")
    public AuditEventResponse get(@PathVariable String id) {
        if (id.isBlank() || id.length() > 64) throw invalid("id");
        AuditLog event = auditLogService.findEvent(id);
        if (event == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AUDIT_EVENT_NOT_FOUND", "error.auditNotFound");
        }
        return AuditEventResponse.from(event);
    }

    private String normalize(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > maxLength) throw invalid(field);
        return value.trim();
    }

    private ApiException invalid(String field) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "error.validation",
                Collections.singletonMap(field, "validation.auditFilter"));
    }

    @Value
    public static class AuditEventResponse {
        String id;
        String operatorId;
        String operationType;
        String targetType;
        String targetId;
        String outcome;
        String traceId;
        Instant createdAt;

        static AuditEventResponse from(AuditLog log) {
            boolean managementTarget = log.getTargetType() != null
                    && Set.of("USER", "APPLICATION", "USER_APPLICATION").contains(log.getTargetType());
            String outcome = log.getOutcome();
            if (outcome == null || !Set.of("success", "failure").contains(outcome)) outcome = "unknown";
            return new AuditEventResponse(log.getId(), log.getOperatorId(), log.getOperationType(),
                    log.getTargetType(), managementTarget ? log.getTargetId() : null,
                    outcome, log.getTraceId(), log.getCreatedAt());
        }
    }
}
