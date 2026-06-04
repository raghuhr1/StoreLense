package com.storelense.soh.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "soh", name = "soh_session_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SohSessionItem {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SohSession session;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "counted_quantity", nullable = false)
    @Builder.Default
    private int countedQuantity = 0;

    @Column(name = "expected_quantity", nullable = false)
    @Builder.Default
    private int expectedQuantity = 0;

    // variance is a generated column in DB — read-only mapping
    @Column(insertable = false, updatable = false)
    private Integer variance;

    @Column(name = "variance_pct", precision = 5, scale = 2)
    private BigDecimal variancePct;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }
}
