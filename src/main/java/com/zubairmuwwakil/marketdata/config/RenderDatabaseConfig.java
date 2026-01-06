package com.zubairmuwwakil.marketdata.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class RenderDatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(RenderDatabaseConfig.class);

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    @ConditionalOnProperty(name = "DATABASE_URL")
    public DataSource dataSource(Environment env) {
        String databaseUrl = env.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            // Fall back to normal Spring properties
            log.debug("No DATABASE_URL env var found, using Spring datasource properties");
            return null;
        }

        try {
            if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
                URI uri = new URI(databaseUrl);
                String userInfo = uri.getUserInfo();
                String username = null;
                String password = null;
                if (userInfo != null) {
                    String[] parts = userInfo.split(":", 2);
                    username = parts[0];
                    if (parts.length > 1) password = parts[1];
                }
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                String database = path != null && path.startsWith("/") ? path.substring(1) : path;
                String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
                HikariConfig cfg = new HikariConfig();
                cfg.setJdbcUrl(jdbcUrl);
                if (username != null) cfg.setUsername(username);
                if (password != null) cfg.setPassword(password);
                cfg.setDriverClassName("org.postgresql.Driver");
                log.info("Configured JDBC URL from DATABASE_URL environment variable");
                return new HikariDataSource(cfg);
            }

            if (databaseUrl.startsWith("jdbc:postgresql://")) {
                // Some platforms provide a JDBC URL that incorrectly includes credentials
                // in the form jdbc:postgresql://user:pass@host:port/db. Extract them.
                Pattern p = Pattern.compile("jdbc:postgresql://(?<user>[^:]+):(?<pass>[^@]+)@(?<hostPort>[^/]+)(?<rest>/.*)");
                Matcher m = p.matcher(databaseUrl);
                if (m.find()) {
                    String user = m.group("user");
                    String pass = m.group("pass");
                    String hostPort = m.group("hostPort");
                    String rest = m.group("rest");
                    String jdbcUrl = "jdbc:postgresql://" + hostPort + rest;
                    HikariConfig cfg = new HikariConfig();
                    cfg.setJdbcUrl(jdbcUrl);
                    cfg.setUsername(user);
                    cfg.setPassword(pass);
                    cfg.setDriverClassName("org.postgresql.Driver");
                    log.info("Parsed JDBC URL with embedded credentials from DATABASE_URL");
                    return new HikariDataSource(cfg);
                }
                // Otherwise assume it's already a valid JDBC URL without credentials
                HikariConfig cfg = new HikariConfig();
                cfg.setJdbcUrl(databaseUrl);
                cfg.setDriverClassName("org.postgresql.Driver");
                log.info("Using provided JDBC DATABASE_URL as-is");
                return new HikariDataSource(cfg);
            }
        } catch (URISyntaxException e) {
            log.error("Failed to parse DATABASE_URL: {}", databaseUrl, e);
        } catch (Exception e) {
            log.error("Error while constructing DataSource from DATABASE_URL", e);
        }

        log.warn("DATABASE_URL was present but could not be parsed; falling back to Spring properties");
        return null;
    }
}
