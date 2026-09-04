package com.hypers.account.config;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.flywaydb.core.api.Location;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AccountFlywayConfiguration {

    @Bean
    public FlywayConfigurationCustomizer accountCommentMigrations() {
        return configuration -> {
            // 保留已配置的公共迁移，仅追加当前数据库的注释方言，避免重复扫描 V6。
            try (var connection = configuration.getDataSource().getConnection()) {
                String vendor = switch (connection.getMetaData().getDatabaseProductName()) {
                    case "PostgreSQL", "H2" -> "postgresql";
                    case "MySQL" -> "mysql";
                    default -> throw new IllegalStateException("Unsupported Account migration database");
                };
                var locations = new LinkedHashSet<String>();
                Arrays.stream(configuration.getLocations()).map(Location::getDescriptor).forEach(locations::add);
                locations.add("classpath:db/vendor/" + vendor);
                configuration.locations(locations.toArray(String[]::new));
            } catch (SQLException exception) {
                throw new IllegalStateException("Unable to select Account migration dialect", exception);
            }
        };
    }
}
