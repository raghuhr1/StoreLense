package com.storelense.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "inventory", name = "exception_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExceptionEvent {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(nullable = false, length = 128)
    private String epc;

    @Column(nullable = false, length = 20)
    private String type;   // MISSING_EPC | GHOST_TAG | READ_MISS | UNDER_REVIEW

    @Column(name = "confidence_score", nullable = false)
    @Builder.Default
    private int confidenceScore = 0;

    @Column(length = 30)
    private String classification;

    @Column(columnDefinition = "jsonb")
    private String reasons;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";   // OPEN | IGNORED | INVESTIGATING | RESOLVED

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
