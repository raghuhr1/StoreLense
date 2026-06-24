package com.storelense.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "inventory", name = "zone_scan_rollup")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ZoneScanRollup {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "scanned_qty", nullable = false)
    @Builder.Default
    private int scannedQty = 0;

    @Column(name = "par_qty", nullable = false)
    @Builder.Default
    private int parQty = 0;

    @Column(name = "min_qty", nullable = false)
    @Builder.Default
    private int minQty = 0;

    @Column(name = "variance", nullable = false)
    @Builder.Default
    private int variance = 0;

    /** ok | low | critical | surplus */
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ok";

    @Column(name = "computed_at", nullable = false)
    private OffsetDateTime computedAt;

    @PrePersist
    void prePersist() {
        if (computedAt == null) computedAt = OffsetDateTime.now();
    }
}
