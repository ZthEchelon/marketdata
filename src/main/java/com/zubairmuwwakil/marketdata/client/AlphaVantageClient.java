package com.zubairmuwwakil.marketdata.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zubairmuwwakil.marketdata.config.MarketDataProperties;
import com.zubairmuwwakil.marketdata.service.ingestion.ApiKeyStore;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AlphaVantageClient {

    private final RestClient restClient;
    private final MarketDataProperties props;
    private final ObjectMapper objectMapper;
    private final ApiKeyStore apiKeyStore;

    public enum KeyValidationStatus {
        VALID,
        QUOTA_EXHAUSTED,
        INVALID
    }

    public record KeyValidationResult(KeyValidationStatus status, String message) {}

    public AlphaVantageClient(MarketDataProperties props, ObjectMapper objectMapper, ApiKeyStore apiKeyStore) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.apiKeyStore = apiKeyStore;

        var av = props.alphavantage();
        if (av == null || av.baseUrl() == null || av.baseUrl().isBlank()) {
            throw new IllegalStateException("Missing config: marketdata.alphavantage.base-url");
        }

        if (av.apiKey() != null && !av.apiKey().isBlank()) {
            this.apiKeyStore.set(av.apiKey());
        }

        this.restClient = RestClient.builder()
                .baseUrl(av.baseUrl())
                .build();
    }

    public JsonNode timeSeriesDaily(String symbol) {
        String key = apiKeyStore.get();
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

    /**
     * Makes a cheap Alpha Vantage call to verify the given key is usable.
     * This consumes one external request/quota on Alpha Vantage.
     */
    public KeyValidationResult validateKey(String key) {
        if (key == null || key.isBlank()) {
            return new KeyValidationResult(KeyValidationStatus.INVALID, "API key is blank");
        }

        String body = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/query")
                        .queryParam("function", "GLOBAL_QUOTE")
                        .queryParam("symbol", "IBM")
                        .queryParam("apikey", key.trim())
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    throw new RuntimeException("Alpha Vantage error: HTTP " + res.getStatusCode());
                })
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("Note")) {
                return new KeyValidationResult(KeyValidationStatus.QUOTA_EXHAUSTED, root.get("Note").asText());
            }
            if (root.has("Error Message")) {
                return new KeyValidationResult(KeyValidationStatus.INVALID, root.get("Error Message").asText());
            }
            JsonNode quote = root.get("Global Quote");
            if (quote != null && quote.has("05. price")) {
                return new KeyValidationResult(KeyValidationStatus.VALID, "Key is valid");
            }
            return new KeyValidationResult(KeyValidationStatus.INVALID, "Unexpected Alpha Vantage response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Alpha Vantage JSON: " + e.getMessage(), e);
        }
    }
}
