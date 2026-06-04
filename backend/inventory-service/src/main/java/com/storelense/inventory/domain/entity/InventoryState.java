package com.storelense.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "inventory", name = "inventory_state")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InventoryState {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "quantity_on_hand", nullable = false)
    @Builder.Default
    private int quantityOnHand = 0;

    @Column(name = "quantity_expected", nullable = false)
    @Builder.Default
    private int quantityExpected = 0;

    @Column(name = "last_counted_at")
    private OffsetDateTime lastCountedAt;

    @Column(name = "last_soh_session_id")
    private UUID lastSohSessionId;

    @Column(name = "accuracy_pct", precision = 5, scale = 2)
    private BigDecimal accuracyPct;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
