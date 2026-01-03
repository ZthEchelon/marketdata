package com.zubairmuwwakil.marketdata.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pipeline_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineStatus status;

    @Column(name = "symbols_expected", nullable = false)
    private Integer symbolsExpected;

    @Column(name = "symbols_processed", nullable = false)
    private Integer symbolsProcessed;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = Instant.now();
    }
}