package com.storelense.product.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "products", name = "barcodes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Barcode {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "barcode_type", nullable = false, length = 20)
    private String barcodeType;

    @Column(name = "barcode_value", nullable = false, unique = true, length = 100)
    private String barcodeValue;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist void prePersist() { createdAt = OffsetDateTime.now(); }
}
