package com.storelense.erp.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "erp", name = "erp_store_mapping")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ErpStoreMapping {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "erp_store_code", nullable = false, unique = true, length = 50)
    private String erpStoreCode;

    @Column(name = "internal_store_id", nullable = false)
    private UUID internalStoreId;

    @Column(name = "inventory_sync_enabled", nullable = false)
    @Builder.Default
    private boolean inventorySyncEnabled = true;

    @Column(name = "soh_push_enabled", nullable = false)
    @Builder.Default
    private boolean sohPushEnabled = true;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }
}
