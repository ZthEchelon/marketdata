package com.zubairmuwwakil.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class MarketdataApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketdataApplication.class, args);
    }
}