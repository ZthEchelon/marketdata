package com.zubairmuwwakil.marketdata.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.config.MarketDataProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AlphaVantageClient {

    private final RestClient restClient;
    private final MarketDataProperties props;
    private final ObjectMapper objectMapper;

    public AlphaVantageClient(MarketDataProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        var av = props.alphavantage();
        if (av == null || av.baseUrl() == null || av.baseUrl().isBlank()) {
            throw new IllegalStateException("Missing config: marketdata.alphavantage.base-url");
        }

        this.restClient = RestClient.builder()
                .baseUrl(av.baseUrl())
                .build();
    }

    public JsonNode timeSeriesDaily(String symbol) {
        String key = props.alphavantage().apiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("ALPHAVANTAGE_API_KEY is missing/blank");
        }

        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "TIME_SERIES_DAILY")
                        .queryParam("symbol", symbol)
                        .queryParam("outputsize", props.alphavantage().outputsize())
                        .queryParam("apikey", key)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new RuntimeException("Alpha Vantage error: HTTP " + res.getStatusCode());
                })
                .body(String.class);

        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Alpha Vantage JSON: " + e.getMessage(), e);
        }
    }
}