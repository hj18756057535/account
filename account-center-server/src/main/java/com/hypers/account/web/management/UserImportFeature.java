package com.hypers.account.web.management;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserImportFeature {
    private final Environment environment;

    public boolean enabled() {
        // 导入使用数据库事务；演示内存 Store 不暴露无法原子回滚的入口。
        return environment.getProperty("account.user-import.enabled", Boolean.class, true)
                && environment.getProperty("account.console-api.enabled", Boolean.class, true)
                && "mybatis".equals(environment.getProperty("account.store.type", "mybatis"));
    }
}
