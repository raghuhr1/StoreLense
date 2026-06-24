package com.storelense.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "inventory", name = "replenishment_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReplenishmentRule {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    /** 'low' fires for low + critical rows; 'critical' fires only for critical. */
    @Column(name = "trigger_status", nullable = false, length = 20)
    @Builder.Default
    private String triggerStatus = "low";

    /** Refill task priority (1 = highest, 10 = lowest). */
    @Column(nullable = false)
    @Builder.Default
    private short priority = 5;

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
    void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
