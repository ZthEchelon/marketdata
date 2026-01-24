package com.zubairmuwwakil.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@EnableScheduling
@SpringBootApplication
public class MarketdataApplication {
    public static void main(String[] args) {
        normalizeJdbcUrlFromEnv();
        SpringApplication.run(MarketdataApplication.class, args);
    }

    private static void normalizeJdbcUrlFromEnv() {
        String rawUrl = firstNonBlank(System.getenv("SPRING_DATASOURCE_URL"), System.getenv("DATABASE_URL"));
        if (rawUrl == null || rawUrl.startsWith("jdbc:")) {
            return;
        }

        if (rawUrl.startsWith("postgres://")) {
            setDatasourceUrl("jdbc:postgresql://" + rawUrl.substring("postgres://".length()));
        } else if (rawUrl.startsWith("postgresql://")) {
            setDatasourceUrl("jdbc:" + rawUrl);
        }
    }

    private static void setDatasourceUrl(String jdbcUrl) {
        System.setProperty("SPRING_DATASOURCE_URL", jdbcUrl);
        System.setProperty("SPRING_FLYWAY_URL", jdbcUrl);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
