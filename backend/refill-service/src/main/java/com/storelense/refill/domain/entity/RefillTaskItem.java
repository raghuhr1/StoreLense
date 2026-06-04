package com.storelense.refill.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "refill", name = "refill_task_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefillTaskItem {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private RefillTask task;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "requested_quantity", nullable = false)
    @Builder.Default
    private int requestedQuantity = 0;

    @Column(name = "fulfilled_quantity", nullable = false)
    @Builder.Default
    private int fulfilledQuantity = 0;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "skip_reason", columnDefinition = "TEXT")
    private String skipReason;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }
}
