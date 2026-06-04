package com.storelense.refill.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "refill", name = "refill_assignments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefillAssignment {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false, unique = true)
    private RefillTask task;

    @Column(name = "assigned_to", nullable = false)
    private UUID assignedTo;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;

    @Column(name = "accepted_at")   private OffsetDateTime acceptedAt;
    @Column(name = "started_at")    private OffsetDateTime startedAt;
    @Column(name = "completed_at")  private OffsetDateTime completedAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "assigned";

    @Column(columnDefinition = "TEXT")
    private String notes;

    @PrePersist void prePersist() { assignedAt = OffsetDateTime.now(); }
}
