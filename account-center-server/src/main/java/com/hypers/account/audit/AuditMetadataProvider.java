package com.hypers.account.audit;

import com.hypers.account.auth.AccountSessionUser;
import com.hypers.account.web.AuthController;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** 可信会话优先，内部命令次之；没有操作人时明确记录为 system。 */
@Component
@RequiredArgsConstructor
public class AuditMetadataProvider {
    private final Clock clock;

    public Metadata current(Object parameter, boolean insert) {
        String actor = sessionActor();
        if (actor == null) actor = commandActor(parameter, insert);
        return new Metadata(actor == null ? "system" : actor, clock.instant().truncatedTo(ChronoUnit.MILLIS));
    }

    public void fill(Object entity, boolean insert, String operator) {
        Metadata stamp = current(operator == null ? null : Map.of("operatorId", operator), insert);
        var bean = SystemMetaObject.forObject(entity);
        if (insert) {
            bean.setValue("createdBy", stamp.getActor());
            bean.setValue("createdAt", stamp.getTime());
        }
        bean.setValue("updatedBy", stamp.getActor());
        bean.setValue("updatedAt", stamp.getTime());
    }

    private String sessionActor() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return null;
        var session = attributes.getRequest().getSession(false);
        if (session == null) return null;
        Object identity = session.getAttribute(AuthController.SESSION_USER_KEY);
        return identity instanceof AccountSessionUser user ? user.getUserId() : null;
    }

    private String commandActor(Object parameter, boolean insert) {
        if (parameter == null) return null;
        var meta = SystemMetaObject.forObject(parameter);
        for (String field : new String[]{"operatorId", "principalName"}) {
            if (meta.hasGetter(field) && meta.getValue(field) instanceof String actor && !actor.isBlank()) return actor;
        }
        if (parameter instanceof Map<?, ?> values) {
            for (String key : new String[]{"record", "batch", "user", "app", "state", "log"}) {
                Object value = values.containsKey(key) ? values.get(key) : null;
                if (value == null) continue;
                var bean = SystemMetaObject.forObject(value);
                for (String field : new String[]{"operatorId", insert ? "createdBy" : "updatedBy"}) {
                    if (bean.hasGetter(field) && bean.getValue(field) instanceof String actor && !actor.isBlank()) return actor;
                }
            }
            for (String key : new String[]{"users", "links"}) {
                if (insert && values.containsKey(key) && values.get(key) instanceof Iterable<?> rows) {
                    for (Object row : rows) {
                        var bean = SystemMetaObject.forObject(row);
                        if (bean.hasGetter("createdBy") && bean.getValue("createdBy") instanceof String actor && !actor.isBlank()) return actor;
                    }
                }
            }
        }
        return null;
    }

    @Value
    public static class Metadata {
        String actor;
        Instant time;
    }
}
