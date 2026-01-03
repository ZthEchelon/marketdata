package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.entity.TechnicalIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TechnicalIndicatorRepository extends JpaRepository<TechnicalIndicator, Long> {

    Optional<TechnicalIndicator> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    List<TechnicalIndicator> findAllBySymbolOrderByTradeDateAsc(String symbol);

    List<TechnicalIndicator> findAllBySymbolAndTradeDateBetweenOrderByTradeDateAsc(
            String symbol, LocalDate from, LocalDate to
    );
}