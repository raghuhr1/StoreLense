package com.storelense.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "inventory", name = "inbound_shipments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InboundShipment {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "dc_code", length = 50)
    private String dcCode;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "expected";

    @Column(name = "expected_at")
    private OffsetDateTime expectedAt;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Column(name = "line_count", nullable = false)
    @Builder.Default
    private int lineCount = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }
}
