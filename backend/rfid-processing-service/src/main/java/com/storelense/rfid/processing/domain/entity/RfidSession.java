package com.storelense.rfid.processing.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "rfid", name = "rfid_sessions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RfidSession {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "soh_session_id")
    private UUID sohSessionId;

    @Column(name = "reader_id")
    private UUID readerId;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "session_type", nullable = false, length = 20)
    @Builder.Default
    private String sessionType = "soh";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "open";

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "total_read_count", nullable = false)
    @Builder.Default
    private int totalReadCount = 0;

    @Column(name = "unique_epc_count", nullable = false)
    @Builder.Default
    private int uniqueEpcCount = 0;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist void prePersist() {
        startedAt = OffsetDateTime.now();
        createdAt = OffsetDateTime.now();
    }
}
