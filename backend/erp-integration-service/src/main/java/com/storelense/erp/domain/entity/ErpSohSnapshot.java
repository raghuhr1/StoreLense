package com.storelense.erp.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "erp", name = "erp_soh_snapshot")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ErpSohSnapshot {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private ErpImportBatch batch;

    @Column(nullable = false, length = 30)
    private String ean;

    @Column(name = "expected_qty", nullable = false)
    @Builder.Default
    private int expectedQty = 0;

    @Column(name = "zone_region", length = 100)
    private String zoneRegion;

    @Column(name = "resolution_status", nullable = false, length = 20)
    @Builder.Default
    private String resolutionStatus = "RAW";   // RAW | RESOLVED | PARTIAL | UNRESOLVED

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
