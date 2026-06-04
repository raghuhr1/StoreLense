package com.storelense.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "inventory", name = "epc_registry")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EpcRegistry {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 128)
    private String epc;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "in_store";

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(name = "last_seen_by_reader_id")
    private UUID lastSeenByReaderId;

    @Column(name = "first_seen_at")
    private OffsetDateTime firstSeenAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() {
        firstSeenAt = lastSeenAt = OffsetDateTime.now();
        createdAt   = updatedAt  = OffsetDateTime.now();
    }
    @PreUpdate void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
