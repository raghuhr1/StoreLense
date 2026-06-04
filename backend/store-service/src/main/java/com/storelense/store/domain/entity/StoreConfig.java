package com.storelense.store.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "stores", name = "store_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoreConfig {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false, unique = true)
    private Store store;

    @Column(name = "rfid_power_dbm", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal rfidPowerDbm = new BigDecimal("30.0");

    @Column(name = "rfid_session", nullable = false)
    @Builder.Default
    private short rfidSession = 2;

    @Column(name = "rfid_target", nullable = false, length = 5)
    @Builder.Default
    private String rfidTarget = "A";

    @Column(name = "soh_schedule_cron", length = 100)
    private String sohScheduleCron;

    @Column(name = "refill_auto_assign", nullable = false)
    @Builder.Default
    private boolean refillAutoAssign = false;

    @Column(name = "erp_sync_enabled", nullable = false)
    @Builder.Default
    private boolean erpSyncEnabled = true;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }
}
