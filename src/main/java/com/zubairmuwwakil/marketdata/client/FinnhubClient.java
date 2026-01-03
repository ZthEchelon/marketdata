package com.zubairmuwwakil.marketdata.client;

import com.zubairmuwwakil.marketdata.config.MarketDataProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

@Component
public class FinnhubClient {

    private final RestClient restClient;
    private final MarketDataProperties props;

    public FinnhubClient(MarketDataProperties props) {
        this.props = props;
        this.restClient = RestClient.builder()
                .baseUrl(props.finnhub().baseUrl())
                .build();
    }

    public FinnhubCandleResponse getDailyCandles(String symbol, Instant from, Instant to) {
    String key = props.finnhub().apiKey();
    if (key == null || key.isBlank()) {
        throw new IllegalStateException("FINNHUB_API_KEY is missing/blank");
    }

    return restClient.get()
            .uri(uriBuilder -> uriBuilder
                    .path("/stock/candle")
                    .queryParam("symbol", symbol)
                    .queryParam("resolution", "D")
                    .queryParam("from", from.getEpochSecond())
                    .queryParam("to", to.getEpochSecond())
                    .build())
            .header("X-Finnhub-Token", key)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                throw new RuntimeException("Finnhub error: HTTP " + res.getStatusCode());
            })
            .body(FinnhubCandleResponse.class);
}

    // Minimal DTO for Finnhub candles
    public record FinnhubCandleResponse(
            String s,        // status: "ok" or "no_data"
            List<Long> t,    // timestamps
            List<Double> o,  // open
            List<Double> h,  // high
            List<Double> l,  // low
            List<Double> c,  // close
            List<Long> v     // volume
    ) {}
}