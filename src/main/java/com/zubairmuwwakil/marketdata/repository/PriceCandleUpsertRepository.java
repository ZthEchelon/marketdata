package com.zubairmuwwakil.marketdata.repository;

import com.zubairmuwwakil.marketdata.model.dto.DailyCandle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PriceCandleUpsertRepository {

    private final JdbcTemplate jdbcTemplate;

    public PriceCandleUpsertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int upsertAll(String symbol, List<DailyCandle> candles, boolean adjusted, String source) {
        if (candles == null || candles.isEmpty()) {
            return 0;
        }
        String sql = """
            INSERT INTO price_candle
                (symbol, trade_date, open, high, low, close, volume, adjusted, source)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (symbol, trade_date) DO UPDATE
                SET open = EXCLUDED.open,
                    high = EXCLUDED.high,
                    low = EXCLUDED.low,
                    close = EXCLUDED.close,
                    volume = EXCLUDED.volume,
                    adjusted = EXCLUDED.adjusted,
                    source = EXCLUDED.source
            """;

        int[][] counts = jdbcTemplate.batchUpdate(sql, candles, candles.size(), (ps, c) -> {
            ps.setString(1, symbol);
            ps.setObject(2, c.tradeDate());
            ps.setBigDecimal(3, c.open());
            ps.setBigDecimal(4, c.high());
            ps.setBigDecimal(5, c.low());
            ps.setBigDecimal(6, c.close());
            ps.setLong(7, c.volume());
            ps.setBoolean(8, adjusted);
            ps.setString(9, source);
        });
        int total = 0;
        for (int[] batch : counts) {
            for (int c : batch) {
                total += c;
            }
        }
        return total;
    }
}