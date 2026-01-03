package com.zubairmuwwakil.marketdata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "marketdata")
public record MarketDataProperties(
        Finnhub finnhub,
        List<String> symbols
) {
    public record Finnhub(String baseUrl, String apiKey) {}
}