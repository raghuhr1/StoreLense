package com.storelense.store.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "stores", name = "rfid_readers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RfidReader {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "zone_id")
    private UUID zoneId;

    @Column(name = "reader_code", nullable = false, length = 50)
    private String readerCode;

    @Column(name = "reader_type", nullable = false, length = 20)
    private String readerType;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "mac_address", columnDefinition = "macaddr")
    private String macAddress;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "antenna_count", nullable = false)
    @Builder.Default
    private short antennaCount = 4;

    @Column(name = "tx_power_dbm", precision = 5, scale = 2)
    private BigDecimal txPowerDbm;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_heartbeat_at")
    private OffsetDateTime lastHeartbeatAt;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }
}
