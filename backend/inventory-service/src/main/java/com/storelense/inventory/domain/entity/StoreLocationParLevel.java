package com.storelense.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "inventory", name = "store_location_par_levels")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoreLocationParLevel {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    /** SALES_FLOOR or BACKROOM. Par levels are set against Sales Floor; Backroom is the buffer. */
    @Column(name = "location_code", nullable = false, length = 20)
    @Builder.Default
    private String locationCode = "SALES_FLOOR";

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "par_qty", nullable = false)
    @Builder.Default
    private int parQty = 1;

    @Column(name = "min_qty", nullable = false)
    @Builder.Default
    private int minQty = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
