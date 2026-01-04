package com.zubairmuwwakil.marketdata.service.ingestion;

import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import com.zubairmuwwakil.marketdata.model.entity.PipelineRun;
import com.zubairmuwwakil.marketdata.model.entity.PipelineStatus;
import com.zubairmuwwakil.marketdata.model.entity.PriceCandle;
import com.zubairmuwwakil.marketdata.repository.PipelineRunRepository;
import com.zubairmuwwakil.marketdata.repository.PriceCandleRepository;
import com.zubairmuwwakil.marketdata.repository.WatchlistSymbolRepository;
import com.zubairmuwwakil.marketdata.service.indicator.IndicatorCalculationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Locale;

@Service
public class IngestionService {

    private final MarketDataProvider marketDataProvider;
    private final WatchlistSymbolRepository watchlistRepo;
    private final QuotaService quotaService;
    private final PriceCandleRepository candleRepo;
    private final PipelineRunRepository runRepo;
    private final IndicatorCalculationService indicatorService;

    public IngestionService(
            MarketDataProvider marketDataProvider,
            WatchlistSymbolRepository watchlistRepo,
            QuotaService quotaService,
            PriceCandleRepository candleRepo,
            PipelineRunRepository runRepo,
            IndicatorCalculationService indicatorService
    ) {
        this.marketDataProvider = marketDataProvider;
        this.watchlistRepo = watchlistRepo;
        this.quotaService = quotaService;
        this.candleRepo = candleRepo;
        this.runRepo = runRepo;
        this.indicatorService = indicatorService;
    }

    /**
     * Daily ingestion entry point.
     * Idempotent, quota-aware, indicator-safe.
     */
    public PipelineRun ingestDaily() {
        Instant startedAt = Instant.now();
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDate runDate = LocalDate.now(ny);

        var activeSymbols =
                watchlistRepo.findAllByActiveTrueOrderBySymbolAsc();

        int expected = activeSymbols.size();
        int remainingQuota = quotaService.remainingToday();
        int willAttempt = Math.min(expected, remainingQuota);

        int processed = 0;
        int failed = 0;
        String lastError = null;

        LocalDate to = LocalDate.now(ny);
        LocalDate from = to.minusDays(120); // warmup for RSI/MACD

        // No quota → fail fast
        if (willAttempt == 0 && expected > 0) {
            return runRepo.save(
                    PipelineRun.builder()
                            .runDate(runDate)
                            .status(PipelineStatus.FAILED)
                            .symbolsExpected(expected)
                            .symbolsProcessed(0)
                            .errorMessage("Daily Alpha Vantage quota exhausted.")
                            .startedAt(startedAt)
                            .finishedAt(Instant.now())
                            .build()
            );
        }

        List<String> symbolsToProcess = activeSymbols.stream()
                .limit(willAttempt)
                .map(s -> s.getSymbol().trim().toUpperCase(Locale.ROOT))
                .toList();

        for (String symbol : symbolsToProcess) {
            try {
                boolean changed = processSymbol(symbol, from, to);
                if (changed) {
                    processed++;
                }
            } catch (Exception e) {
                failed++;
                lastError = symbol + ": " + e.getMessage();
            }
        }

        PipelineStatus status;
        if (processed == 0 && failed > 0) {
            status = PipelineStatus.FAILED;
        } else if (failed > 0) {
            status = PipelineStatus.PARTIAL;
        } else {
            status = PipelineStatus.SUCCESS;
        }

        String errorMessage =
                failed == 0 ? null
                        : "Failed " + failed + "/" + willAttempt +
                          ". Last error: " + lastError;

        return runRepo.save(
                PipelineRun.builder()
                        .runDate(runDate)
                        .status(status)
                        .symbolsExpected(expected)
                        .symbolsProcessed(processed)
                        .errorMessage(errorMessage)
                        .startedAt(startedAt)
                        .finishedAt(Instant.now())
                        .build()
        );
    }

    /**
     * Processes one symbol in its own transaction so a failure
     * does not roll back the entire pipeline run.
     *
     * @return true if any candles were inserted and indicators recalculated.
     */
    @Transactional
    protected boolean processSymbol(String symbol, LocalDate from, LocalDate to) {
        quotaService.consumeOneCall();

        List<DailyCandle> candles =
                marketDataProvider.fetchDailyCandles(symbol, from, to);

        int inserted = 0;

        for (DailyCandle c : candles) {
            if (candleRepo
                    .findBySymbolAndTradeDate(symbol, c.tradeDate())
                    .isPresent()) {
                continue;
            }

            candleRepo.save(
                    PriceCandle.builder()
                            .symbol(symbol)
                            .tradeDate(c.tradeDate())
                            .open(c.open())
                            .high(c.high())
                            .low(c.low())
                            .close(c.close())
                            .volume(c.volume())
                            .adjusted(false)
                            .source(marketDataProvider.sourceName())
                            .build()
            );
            inserted++;
        }

        // Always run indicators; service is idempotent and will fill gaps
        indicatorService.calculateForSymbol(symbol);
        return true;
    }
}
