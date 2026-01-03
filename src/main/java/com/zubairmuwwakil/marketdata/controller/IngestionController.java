///temporary manual trigger

package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.entity.PipelineRun;
import com.zubairmuwwakil.marketdata.service.ingestion.FinnhubIngestionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class IngestionController {

    private final FinnhubIngestionService ingestionService;

    public IngestionController(FinnhubIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public PipelineRun ingestNow() {
        return ingestionService.ingestPreviousTradingDay();
    }
}