package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.client.FinnhubClient;
import com.zubairmuwwakil.marketdata.config.MarketDataProperties;
import com.zubairmuwwakil.marketdata.model.entity.PipelineRun;
import com.zubairmuwwakil.marketdata.model.entity.PipelineStatus;
import com.zubairmuwwakil.marketdata.model.entity.PriceCandle;
import com.zubairmuwwakil.marketdata.repository.PipelineRunRepository;
import com.zubairmuwwakil.marketdata.repository.PriceCandleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.Locale;

@Service
public class FinnhubIngestionService {

    private final FinnhubClient finnhubClient;
    private final MarketDataProperties props;
    private final PriceCandleRepository candleRepo;
    private final PipelineRunRepository runRepo;

    public FinnhubIngestionService(
            FinnhubClient finnhubClient,
            MarketDataProperties props,
            PriceCandleRepository candleRepo,
            PipelineRunRepository runRepo
    ) {
        this.finnhubClient = finnhubClient;
        this.props = props;
        this.candleRepo = candleRepo;
        this.runRepo = runRepo;
    }

    @Transactional
    public PipelineRun ingestPreviousTradingDay() {
        var startedAt = Instant.now();
        var runDate = LocalDate.now(ZoneId.of("America/New_York"));

        int expected = props.symbols().size();
        int processed = 0;
        PipelineStatus status = PipelineStatus.SUCCESS;
        String errorMessage = null;

        // Simple approach for now: fetch last ~40 days to ensure indicator warmup later
        Instant to = Instant.now();
        Instant from = to.minus(Duration.ofDays(60));

        for (String raw : props.symbols()) {
            String symbol = raw.trim().toUpperCase(Locale.ROOT);
            try {
                var resp = finnhubClient.getDailyCandles(symbol, from, to);

                if (resp == null || resp.s() == null || !"ok".equalsIgnoreCase(resp.s())) {
                    // no_data is not a fatal error
                    continue;
                }

                // Persist every candle returned
                for (int i = 0; i < resp.t().size(); i++) {
                    LocalDate tradeDate = Instant.ofEpochSecond(resp.t().get(i))
                            .atZone(ZoneId.of("America/New_York"))
                            .toLocalDate();

                    // idempotent: unique(symbol, trade_date) prevents dupes
                    if (candleRepo.findBySymbolAndTradeDate(symbol, tradeDate).isPresent()) {
                        continue;
                    }

                    PriceCandle candle = PriceCandle.builder()
                            .symbol(symbol)
                            .tradeDate(tradeDate)
                            .open(BigDecimal.valueOf(resp.o().get(i)))
                            .high(BigDecimal.valueOf(resp.h().get(i)))
                            .low(BigDecimal.valueOf(resp.l().get(i)))
                            .close(BigDecimal.valueOf(resp.c().get(i)))
                            .volume(resp.v().get(i))
                            .adjusted(false)
                            .build();

                    candleRepo.save(candle);
                }

                processed++;
            } catch (Exception e) {
                status = PipelineStatus.PARTIAL;
                errorMessage = "At least one symbol failed. Last error: " + e.getMessage();
            }
        }

        PipelineRun run = PipelineRun.builder()
                .runDate(runDate)
                .status(status)
                .symbolsExpected(expected)
                .symbolsProcessed(processed)
                .errorMessage(errorMessage)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .build();

        return runRepo.save(run);
    }
}