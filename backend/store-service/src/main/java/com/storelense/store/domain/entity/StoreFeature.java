package com.storelense.store.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "stores", name = "store_features")
@IdClass(StoreFeatureId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoreFeature {

    @Id
    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Id
    @Column(name = "feature", nullable = false, length = 40)
    private String feature;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist @PreUpdate
    void touch() { updatedAt = OffsetDateTime.now(); }
}
