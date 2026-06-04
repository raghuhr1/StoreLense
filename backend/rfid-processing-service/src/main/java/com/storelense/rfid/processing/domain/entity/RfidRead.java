package com.storelense.rfid.processing.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "rfid", name = "rfid_reads")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RfidRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rfid_session_id", nullable = false)
    private UUID rfidSessionId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "reader_id")
    private UUID readerId;

    @Column(nullable = false, length = 128)
    private String epc;

    @Column(precision = 6, scale = 2)
    private BigDecimal rssi;

    @Column(name = "antenna_port")
    private Short antennaPort;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist void prePersist() { createdAt = OffsetDateTime.now(); }
}
