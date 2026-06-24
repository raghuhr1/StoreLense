package com.storelense.inventory.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "inventory", name = "epc_position_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EpcPositionHistory {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 128)
    private String epc;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "from_zone_id")
    private UUID fromZoneId;

    @Column(name = "to_zone_id")
    private UUID toZoneId;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "moved_at", nullable = false)
    private OffsetDateTime movedAt;

    @Column(name = "triggered_by", nullable = false, length = 50)
    @Builder.Default
    private String triggeredBy = "scan_session";

    @Column(name = "session_id")
    private UUID sessionId;

    @PrePersist
    void prePersist() {
        if (movedAt == null) movedAt = OffsetDateTime.now();
    }
}
