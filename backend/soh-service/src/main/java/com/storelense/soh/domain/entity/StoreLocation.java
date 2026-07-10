package com.storelense.soh.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "soh", name = "store_locations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoreLocation {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    /** SALES_FLOOR or BACKROOM */
    @Column(name = "location_code", nullable = false, length = 20)
    private String locationCode;

    /** MENS | WOMENS | KIDS | FOOTWEAR | ACCESSORIES — null for BACKROOM and unsectioned SALES_FLOOR */
    @Column(name = "section_code", length = 20)
    private String sectionCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private short sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist void prePersist() { createdAt = OffsetDateTime.now(); }
}
