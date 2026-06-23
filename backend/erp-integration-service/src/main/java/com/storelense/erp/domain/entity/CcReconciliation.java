package com.storelense.erp.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "erp", name = "cc_reconciliation")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CcReconciliation {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String mode = "ERP_DRIVEN";   // ERP_DRIVEN | SYSTEM_DRIVEN

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "run_at", nullable = false)
    private OffsetDateTime runAt;

    @Column(name = "total_expected", nullable = false)
    @Builder.Default private int totalExpected = 0;

    @Column(name = "total_scanned", nullable = false)
    @Builder.Default private int totalScanned = 0;

    @Column(name = "matched_count", nullable = false)
    @Builder.Default private int matchedCount = 0;

    @Column(name = "missing_count", nullable = false)
    @Builder.Default private int missingCount = 0;

    @Column(name = "extra_count", nullable = false)
    @Builder.Default private int extraCount = 0;

    @Column(name = "accuracy_pct", precision = 5, scale = 2)
    private BigDecimal accuracyPct;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "RUNNING";   // RUNNING | COMPLETED | FAILED

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (runAt == null)     runAt     = OffsetDateTime.now();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
