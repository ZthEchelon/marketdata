package com.zubairmuwwakil.marketdata.controller;

import com.zubairmuwwakil.marketdata.model.entity.PipelineRun;
import com.zubairmuwwakil.marketdata.service.ingestion.IngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<PipelineRun> ingest() {
        return ResponseEntity.ok(ingestionService.ingestDaily());
    }
}