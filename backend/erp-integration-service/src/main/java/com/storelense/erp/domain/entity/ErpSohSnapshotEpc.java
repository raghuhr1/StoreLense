package com.storelense.erp.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "erp", name = "erp_soh_snapshot_epcs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ErpSohSnapshotEpc {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private ErpSohSnapshot snapshot;

    @Column(nullable = false, length = 100)
    private String epc;

    @Column(name = "matched_by", nullable = false, length = 30)
    private String matchedBy;   // SGTIN96 | INBOUND_COMMISSION | MANUAL

    @Column(name = "resolved_at", nullable = false)
    private OffsetDateTime resolvedAt;

    @PrePersist
    void prePersist() {
        if (resolvedAt == null) resolvedAt = OffsetDateTime.now();
    }
}
