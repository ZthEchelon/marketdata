package com.zubairmuwwakil.marketdata.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
    name = "technical_indicator",
    uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "trade_date"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TechnicalIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "rsi_14", precision = 10, scale = 4)
    private BigDecimal rsi14;

    @Column(precision = 19, scale = 6)
    private BigDecimal macd;

    @Column(name = "macd_signal", precision = 19, scale = 6)
    private BigDecimal macdSignal;

    @Column(name = "macd_histogram", precision = 19, scale = 6)
    private BigDecimal macdHistogram;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}