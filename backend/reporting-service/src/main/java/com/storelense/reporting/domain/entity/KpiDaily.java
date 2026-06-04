package com.storelense.reporting.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "reporting", name = "kpi_daily")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KpiDaily {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "kpi_date", nullable = false)
    private LocalDate kpiDate;

    @Column(name = "inventory_accuracy_pct", precision = 5, scale = 2)
    private BigDecimal inventoryAccuracyPct;

    @Column(name = "soh_sessions_count", nullable = false)
    @Builder.Default private int sohSessionsCount = 0;

    @Column(name = "refill_tasks_created", nullable = false)
    @Builder.Default private int refillTasksCreated = 0;

    @Column(name = "refill_tasks_completed", nullable = false)
    @Builder.Default private int refillTasksCompleted = 0;

    @Column(name = "refill_completion_rate_pct", precision = 5, scale = 2)
    private BigDecimal refillCompletionRatePct;

    @Column(name = "avg_refill_time_minutes", precision = 8, scale = 2)
    private BigDecimal avgRefillTimeMinutes;

    @Column(name = "total_epc_reads", nullable = false)
    @Builder.Default private long totalEpcReads = 0;

    @Column(name = "unique_skus_counted", nullable = false)
    @Builder.Default private int uniqueSkusCounted = 0;

    @Column(name = "variance_items_count", nullable = false)
    @Builder.Default private int varianceItemsCount = 0;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist void prePersist() { createdAt = OffsetDateTime.now(); }
}
