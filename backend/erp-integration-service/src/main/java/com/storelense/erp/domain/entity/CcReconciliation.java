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

    /**
     * Single-session reconciliation anchor. Nullable when the reconciliation
     * spans multiple sessions via cycleCountId.
     */
    @Column(name = "session_id")
    private UUID sessionId;

    /**
     * Cycle-count-scoped reconciliation anchor. When set, this reconciliation
     * covers all sessions belonging to the parent cycle count.
     */
    @Column(name = "cycle_count_id")
    private UUID cycleCountId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String mode = "ERP_DRIVEN";   // ERP_DRIVEN | SYSTEM_DRIVEN

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "run_at", nullable = false)
    private OffsetDateTime runAt;

    // ── Store-level totals ────────────────────────────────────────────────────

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

    // ── Sales Floor breakdown ─────────────────────────────────────────────────

    @Column(name = "floor_expected", nullable = false)
    @Builder.Default private int floorExpected = 0;

    @Column(name = "floor_scanned", nullable = false)
    @Builder.Default private int floorScanned = 0;

    @Column(name = "floor_missing", nullable = false)
    @Builder.Default private int floorMissing = 0;

    // ── Backroom breakdown ────────────────────────────────────────────────────

    @Column(name = "backroom_expected", nullable = false)
    @Builder.Default private int backroomExpected = 0;

    @Column(name = "backroom_scanned", nullable = false)
    @Builder.Default private int backroomScanned = 0;

    @Column(name = "backroom_missing", nullable = false)
    @Builder.Default private int backroomMissing = 0;

    // ── Approval ─────────────────────────────────────────────────────────────

    @Column(name = "reviewer_id")
    private UUID reviewerId;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    /**
     * RUNNING → COMPLETED → PENDING_APPROVAL → APPROVED
     * Any state → FAILED
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "RUNNING";

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (runAt == null)     runAt     = OffsetDateTime.now();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
