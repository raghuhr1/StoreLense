package com.storelense.erp.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "erp", name = "erp_product_mapping")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ErpProductMapping {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "erp_product_code", nullable = false, unique = true, length = 100)
    private String erpProductCode;

    @Column(name = "internal_sku", nullable = false, length = 100)
    private String internalSku;

    @Column(name = "internal_product_id")
    private UUID internalProductId;    // set after product-service confirms creation

    @Column(name = "erp_product_name", length = 500)
    private String erpProductName;

    @Column(name = "erp_category_code", length = 50)
    private String erpCategoryCode;

    @Column(name = "last_synced_at")
    private OffsetDateTime lastSyncedAt;

    @Column(name = "sync_hash", length = 64)
    private String syncHash;           // SHA-256 of last synced payload — skip if unchanged

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }
}
