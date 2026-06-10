package com.storelense.erp.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "erp", name = "erp_import_batch")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ErpImportBatch {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "source_type", nullable = false, length = 10)
    private String sourceType;   // FILE | S3

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";   // PENDING | PROCESSING | COMPLETED | FAILED

    @Column(name = "total_rows")
    @Builder.Default
    private int totalRows = 0;

    @Column(name = "resolved_rows")
    @Builder.Default
    private int resolvedRows = 0;

    @Column(name = "unresolved_rows")
    @Builder.Default
    private int unresolvedRows = 0;

    @Column(name = "imported_at")
    private OffsetDateTime importedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = OffsetDateTime.now();
    }
}
