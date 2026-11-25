package com.hypers.account.web.management;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserImportFeature {
    private final Environment environment;

    public boolean enabled() {
        // 导入依赖数据库事务和管理端接口，两者都开启时才暴露入口。
        return environment.getProperty("account.user-import.enabled", Boolean.class, true)
                && environment.getProperty("account.console-api.enabled", Boolean.class, true);
    }
}
