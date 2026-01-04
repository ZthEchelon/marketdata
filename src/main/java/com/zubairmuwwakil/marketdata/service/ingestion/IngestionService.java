package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.entity.PipelineRun;
import com.zubairmuwwakil.marketdata.model.entity.PipelineStatus;
import com.zubairmuwwakil.marketdata.model.entity.PriceCandle;
import com.zubairmuwwakil.marketdata.repository.PipelineRunRepository;
import com.zubairmuwwakil.marketdata.repository.PriceCandleRepository;
import com.zubairmuwwakil.marketdata.repository.WatchlistSymbolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Locale;

@Service
public class IngestionService {

    private final MarketDataProvider marketDataProvider; // AlphaVantageDailyProvider
    private final WatchlistSymbolRepository watchlistRepo;
    private final QuotaService quotaService;
    private final PriceCandleRepository candleRepo;
    private final PipelineRunRepository runRepo;

    public IngestionService(
            MarketDataProvider marketDataProvider,
            WatchlistSymbolRepository watchlistRepo,
            QuotaService quotaService,
            PriceCandleRepository candleRepo,
            PipelineRunRepository runRepo
    ) {
        this.marketDataProvider = marketDataProvider;
        this.watchlistRepo = watchlistRepo;
        this.quotaService = quotaService;
        this.candleRepo = candleRepo;
        this.runRepo = runRepo;
    }

    @Transactional
    public PipelineRun ingestDaily() {
        Instant startedAt = Instant.now();
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDate runDate = LocalDate.now(ny);

        var active = watchlistRepo.findAllByActiveTrueOrderBySymbolAsc();
        int expected = active.size();

        int remaining = quotaService.remainingToday();
        int willAttempt = Math.min(expected, remaining);

        int processed = 0;   // successful API calls
        int failed = 0;      // failed API calls
        String lastError = null;

        // for RSI/MACD warmup later
        LocalDate to = LocalDate.now(ny);
        LocalDate from = to.minusDays(120);

        // If we have no quota left, mark FAILED (nothing attempted)
        if (willAttempt == 0 && expected > 0) {
            PipelineRun run = PipelineRun.builder()
                    .runDate(runDate)
                    .status(PipelineStatus.FAILED)
                    .symbolsExpected(expected)
                    .symbolsProcessed(0)
                    .errorMessage("Daily Alpha Vantage quota exhausted (0 remaining).")
                    .startedAt(startedAt)
                    .finishedAt(Instant.now())
                    .build();
            return runRepo.save(run);
        }

        List<String> symbolsToProcess = active.stream()
                .limit(willAttempt)
                .map(w -> w.getSymbol().trim().toUpperCase(Locale.ROOT))
                .toList();

        for (String symbol : symbolsToProcess) {
            try {
                // consume BEFORE call so we never exceed limit if the call hangs/retries
                quotaService.consumeOneCall();

                List<DailyCandle> candles =
                        marketDataProvider.fetchDailyCandles(symbol, from, to);

                // Upsert by (symbol, tradeDate) — idempotent
                for (DailyCandle c : candles) {
                    if (candleRepo.findBySymbolAndTradeDate(symbol, c.tradeDate()).isPresent()) {
                        continue;
                    }

                    PriceCandle entity = PriceCandle.builder()
                            .symbol(symbol)
                            .tradeDate(c.tradeDate())
                            .open(c.open())
                            .high(c.high())
                            .low(c.low())
                            .close(c.close())
                            .volume(c.volume())
                            .adjusted(false)
                            .source(marketDataProvider.sourceName())
                            .build();

                    candleRepo.save(entity);
                }

                processed++;
            } catch (Exception e) {
                failed++;
                lastError = symbol + ": " + e.getMessage();
            }
        }

        PipelineStatus status;
        if (processed == 0 && failed > 0) status = PipelineStatus.FAILED;
        else if (failed > 0) status = PipelineStatus.PARTIAL;
        else status = PipelineStatus.SUCCESS;

        String errorMessage = (failed == 0)
                ? null
                : ("Failed " + failed + "/" + willAttempt + ". Last error: " + lastError);

        PipelineRun run = PipelineRun.builder()
                .runDate(runDate)
                .status(status)
                .symbolsExpected(expected)          // how many user wanted
                .symbolsProcessed(processed)        // successful API calls
                .errorMessage(errorMessage)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .build();

        return runRepo.save(run);
    }
}