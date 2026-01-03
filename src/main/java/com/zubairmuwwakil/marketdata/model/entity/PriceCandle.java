 package com.zubairmuwwakil.marketdata.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "price_candle",
    uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "trade_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceCandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal open;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal high;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal low;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal close;

    @Column(nullable = false)
    private Long volume;

    @Column(nullable = false)
    private boolean adjusted = false;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (source == null) source = "FINNHUB";
    }
}