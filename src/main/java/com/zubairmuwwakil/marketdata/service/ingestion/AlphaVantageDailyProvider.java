package com.zubairmuwwakil.marketdata.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.zubairmuwwakil.marketdata.client.AlphaVantageClient;
import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Primary // ensures this is the MarketDataProvider Spring injects
public class AlphaVantageDailyProvider implements MarketDataProvider {

    private final AlphaVantageClient client;

    public AlphaVantageDailyProvider(AlphaVantageClient client) {
        this.client = client;
    }

    @Override
    public List<DailyCandle> fetchDailyCandles(String symbol, LocalDate from, LocalDate to) {
        JsonNode root = client.timeSeriesDaily(symbol);

        if (root.has("Error Message")) {
            throw new RuntimeException(root.get("Error Message").asText());
        }
        if (root.has("Note")) {
            throw new RuntimeException("Alpha Vantage throttled request: " + root.get("Note").asText());
        }

        JsonNode series = root.get("Time Series (Daily)");
        if (series == null) {
            return List.of();
        }

        List<DailyCandle> out = new ArrayList<>();

        Iterator<Map.Entry<String, JsonNode>> it = series.fields();
        while (it.hasNext()) {
            var entry = it.next();
            LocalDate date = LocalDate.parse(entry.getKey());

            if (date.isBefore(from) || date.isAfter(to)) continue;

            JsonNode v = entry.getValue();
            if (v == null ||
                    v.get("1. open") == null ||
                    v.get("2. high") == null ||
                    v.get("3. low") == null ||
                    v.get("4. close") == null ||
                    v.get("5. volume") == null ||
                    v.get("4. close").isNull()) {
                continue; // skip malformed rows
            }
            out.add(new DailyCandle(
                    date,
                    new BigDecimal(v.get("1. open").asText()),
                    new BigDecimal(v.get("2. high").asText()),
                    new BigDecimal(v.get("3. low").asText()),
                    new BigDecimal(v.get("4. close").asText()),
                    v.get("5. volume").asLong()
            ));
        }

        out.sort(Comparator.comparing(DailyCandle::tradeDate));
        return out;
    }

    @Override
    public String sourceName() {
        return "ALPHAVANTAGE";
    }
}
