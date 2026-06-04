package com.storelense.product.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "products", name = "epc_tags")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EpcTag {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String epc;

    @Column(name = "epc_encoding", nullable = false, length = 20)
    @Builder.Default
    private String epcEncoding = "SGTIN_96";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "company_prefix", length = 20)
    private String companyPrefix;

    @Column(name = "item_reference", length = 20)
    private String itemReference;

    @Column(name = "serial_number", length = 30)
    private String serialNumber;

    @Column(name = "is_encoded", nullable = false)
    @Builder.Default
    private boolean encoded = false;

    @Column(name = "encoded_at")
    private OffsetDateTime encodedAt;

    @Column(name = "encoded_by")
    private UUID encodedBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }
}
