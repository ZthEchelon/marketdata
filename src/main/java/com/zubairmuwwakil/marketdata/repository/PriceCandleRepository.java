package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.entity.PriceCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceCandleRepository
        extends JpaRepository<PriceCandle, Long> {

    // Idempotency check during ingestion
    Optional<PriceCandle> findBySymbolAndTradeDate(
            String symbol,
            LocalDate tradeDate
    );

    // Used by IndicatorCalculationService
    List<PriceCandle> findBySymbolOrderByTradeDateAsc(
            String symbol
    );

    Optional<PriceCandle> findTop1BySymbolOrderByTradeDateDesc(String symbol);

    @org.springframework.data.jpa.repository.Query("select distinct p.symbol from PriceCandle p")
    List<String> findDistinctSymbols();

    // (Optional, future use) recent candles
    List<PriceCandle> findTop200BySymbolOrderByTradeDateDesc(
            String symbol
    );
}
