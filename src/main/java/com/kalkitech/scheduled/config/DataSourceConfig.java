package com.kalkitech.scheduled.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean(name = "db1DataSource")
    public DataSource db1DataSource(AppProperties props) {
        var p = props.getDb1();
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(p.getJdbcUrl());
        cfg.setUsername(p.getUsername());
        cfg.setPassword(p.getPassword());
        cfg.setDriverClassName(p.getDriverClassName());
        cfg.setMaximumPoolSize(p.getMaximumPoolSize());
        cfg.setPoolName("db1-pool");
        // DB1 can be temporarily unreachable (VPN/DNS/firewall). Do not fail app startup.
        cfg.setInitializationFailTimeout(-1);
        cfg.setConnectionTimeout(10_000);
        cfg.setValidationTimeout(5_000);
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setKeepaliveTime(30_000);
        cfg.setMinimumIdle(0);
        cfg.setIdleTimeout(60_000);

        cfg.setMaxLifetime(120_000); // 2 min

        return new HikariDataSource(cfg);
    }

    @Bean(name = "db2DataSource")
    public DataSource db2DataSource(AppProperties props) {
        var p = props.getDb2();
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(p.getJdbcUrl());
        cfg.setUsername(p.getUsername());
        cfg.setPassword(p.getPassword());
        cfg.setDriverClassName(p.getDriverClassName());
        cfg.setMaximumPoolSize(p.getMaximumPoolSize());
        cfg.setPoolName("db2-pool");
        // StarRocks can close idle connections; keep them fresh
        cfg.setMaxLifetime(120_000); // 2 min
        cfg.setKeepaliveTime(30_000);
        cfg.setMinimumIdle(0);
        cfg.setIdleTimeout(60_000);
 // 1 min
        cfg.setValidationTimeout(5_000);
        cfg.setConnectionTimeout(10_000);
        return new HikariDataSource(cfg);
    }

    @Bean(name = "db1JdbcTemplate")
    public JdbcTemplate db1JdbcTemplate(@Qualifier("db1DataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean(name = "db2NamedJdbcTemplate")
    public NamedParameterJdbcTemplate db2NamedJdbcTemplate(@Qualifier("db2DataSource") DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}
