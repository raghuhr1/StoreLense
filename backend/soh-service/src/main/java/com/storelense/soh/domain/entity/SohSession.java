package com.storelense.soh.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "soh", name = "soh_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SohSession {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    /** Optional parent cycle count grouping this session with others. */
    @Column(name = "cycle_count_id")
    private UUID cycleCountId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "session_type", nullable = false, length = 20)
    @Builder.Default
    private String sessionType = "manual";

    /**
     * created → in_progress → paused → in_progress (resume)
     *                       → completed → uploaded → reconciled → closed
     * Any state → cancelled | failed
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "created";

    /** SALES_FLOOR or BACKROOM — null for legacy / non-cycle-count sessions. */
    @Column(name = "location_code", length = 20)
    private String locationCode;

    /** MENS | WOMENS | KIDS | FOOTWEAR | ACCESSORIES — only set when locationCode = SALES_FLOOR. */
    @Column(name = "section_code", length = 20)
    private String sectionCode;

    @Column(name = "started_by", nullable = false)
    private UUID startedBy;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "paused_at")
    private OffsetDateTime pausedAt;

    @Column(name = "resumed_at")
    private OffsetDateTime resumedAt;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @Column(name = "reconciled_at")
    private OffsetDateTime reconciledAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "total_epc_reads", nullable = false)
    @Builder.Default
    private int totalEpcReads = 0;

    @Column(name = "unique_epc_count", nullable = false)
    @Builder.Default
    private int uniqueEpcCount = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String source = "manual";

    @Column(name = "zone_region", length = 100)
    private String zoneRegion;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SohSessionItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private SohResult result;

    @PrePersist void prePersist() {
        startedAt = OffsetDateTime.now();
        createdAt = updatedAt = OffsetDateTime.now();
    }
    @PreUpdate void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public boolean isEditable() {
        return "created".equals(status) || "in_progress".equals(status) || "paused".equals(status);
    }
}
