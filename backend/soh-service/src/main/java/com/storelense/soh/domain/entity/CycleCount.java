package com.storelense.soh.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "soh", name = "cycle_counts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CycleCount {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "count_date", nullable = false)
    private LocalDate countDate;

    /**
     * DRAFT → RUNNING → COMPLETED → UPLOADED → RECONCILED → CLOSED
     * Any state can transition to CLOSED by a manager.
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "cycleCountId", fetch = FetchType.LAZY)
    @Builder.Default
    private List<SohSession> sessions = new ArrayList<>();

    @PrePersist void prePersist() {
        if (countDate == null) countDate = LocalDate.now();
        createdAt = updatedAt = OffsetDateTime.now();
    }
    @PreUpdate void preUpdate() { updatedAt = OffsetDateTime.now(); }

    public boolean isEditable() {
        return "DRAFT".equals(status) || "RUNNING".equals(status);
    }
}
