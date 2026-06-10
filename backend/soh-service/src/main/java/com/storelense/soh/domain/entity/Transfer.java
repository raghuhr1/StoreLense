package com.storelense.soh.domain.entity;

import com.storelense.soh.converter.JsonStringListConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "soh", name = "transfers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Transfer {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "source_store_id", nullable = false)
    private UUID sourceStoreId;

    @Column(name = "dest_store_id", nullable = false)
    private UUID destStoreId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";   // PENDING | IN_TRANSIT | RECEIVED | CANCELLED

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Convert(converter = JsonStringListConverter.class)
    @Column(nullable = false, columnDefinition = "jsonb")
    @Builder.Default
    private List<String> epcs = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    @Convert(converter = JsonStringListConverter.class)
    @Column(name = "received_epcs", columnDefinition = "jsonb")
    private List<String> receivedEpcs;

    @PrePersist
    void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }

    @PreUpdate
    void preUpdate() { updatedAt = OffsetDateTime.now(); }
}
