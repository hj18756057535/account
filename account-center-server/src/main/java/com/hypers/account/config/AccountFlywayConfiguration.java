package com.hypers.account.config;

import java.sql.SQLException;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AccountFlywayConfiguration {

    @Bean
    public FlywayConfigurationCustomizer accountMigrations() {
        return configuration -> {
            // 每种数据库独立维护完整迁移，不能同时扫描两份同版本初始化脚本。
            try (var connection = configuration.getDataSource().getConnection()) {
                String vendor = switch (connection.getMetaData().getDatabaseProductName()) {
                    case "PostgreSQL", "H2" -> "postgresql";
                    case "MySQL" -> "mysql";
                    default -> throw new IllegalStateException("Unsupported Account migration database");
                };
                configuration.locations("classpath:db/vendor/" + vendor);
                // 未发布测试库已批准重建；旧库必须显式处理，不能静默跳过新基线。
                configuration.baselineOnMigrate(false);
                configuration.cleanDisabled(true);
            } catch (SQLException exception) {
                throw new IllegalStateException("Unable to select Account migration dialect", exception);
            }
        };
    }
}
