package com.hypers.account.audit;

import java.sql.Connection;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

/** 在 JDBC 绑定前填入独立审计参数，不改业务入参或拼接业务 SQL。 */
@Component
@RequiredArgsConstructor
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
public class AuditSqlInterceptor implements Interceptor {
    private final AuditMetadataProvider metadata;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        var handler = (StatementHandler) invocation.getTarget();
        var sql = handler.getBoundSql();
        if (sql.getParameterMappings().stream().anyMatch(mapping -> mapping.getProperty().startsWith("_audit."))) {
            boolean insert = sql.getSql().stripLeading().toLowerCase(Locale.ROOT).startsWith("insert");
            sql.setAdditionalParameter("_audit", metadata.current(sql.getParameterObject(), insert));
        }
        return invocation.proceed();
    }
}
