package com.storelense.erp.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "erp", name = "erp_sync_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ErpSyncLog {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "sync_type", nullable = false, length = 30)
    private String syncType;        // PRODUCT_INBOUND, INVENTORY_INBOUND, SOH_OUTBOUND

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;       // INBOUND, OUTBOUND

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "running";  // running, completed, failed, partial

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "records_fetched")
    @Builder.Default
    private int recordsFetched = 0;

    @Column(name = "records_published")
    @Builder.Default
    private int recordsPublished = 0;

    @Column(name = "records_failed")
    @Builder.Default
    private int recordsFailed = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "erp_cursor", length = 255)
    private String erpCursor;       // pagination cursor or last-processed ID for incremental sync

    @Column(name = "triggered_by", length = 50)
    @Builder.Default
    private String triggeredBy = "scheduler";   // scheduler, manual, webhook

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist void prePersist() {
        startedAt = createdAt = OffsetDateTime.now();
    }
}
