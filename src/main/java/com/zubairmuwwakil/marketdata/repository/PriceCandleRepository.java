package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.entity.PriceCandle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceCandleRepository extends JpaRepository<PriceCandle, Long> {

    Optional<PriceCandle> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    List<PriceCandle> findAllBySymbolOrderByTradeDateAsc(String symbol);

    List<PriceCandle> findAllBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
            String symbol, LocalDate from, LocalDate to
    );
}