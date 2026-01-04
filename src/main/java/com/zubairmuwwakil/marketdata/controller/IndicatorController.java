package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.dto.IndicatorDto;
import com.zubairmuwwakil.marketdata.model.entity.IndicatorType;
import com.zubairmuwwakil.marketdata.service.indicator.IndicatorQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/indicators")
public class IndicatorController {

    private final IndicatorQueryService service;

    public IndicatorController(IndicatorQueryService service) {
        this.service = service;
    }

    // GET /api/v1/indicators/AAPL
    @GetMapping("/{symbol}")
    public ResponseEntity<List<IndicatorDto>> getAllIndicators(
            @PathVariable String symbol
    ) {
        return ResponseEntity.ok(
                service.getAllForSymbol(symbol.toUpperCase())
        );
    }

    // GET /api/v1/indicators/AAPL/RSI_14
    @GetMapping("/{symbol}/{type}")
    public ResponseEntity<List<IndicatorDto>> getByType(
            @PathVariable String symbol,
            @PathVariable IndicatorType type
    ) {
        return ResponseEntity.ok(
                service.getByType(symbol.toUpperCase(), type)
        );
    }
}
