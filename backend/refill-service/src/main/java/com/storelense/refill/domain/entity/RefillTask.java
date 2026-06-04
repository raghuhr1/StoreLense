package com.storelense.refill.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(schema = "refill", name = "refill_tasks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefillTask {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "task_type", nullable = false, length = 20)
    @Builder.Default
    private String taskType = "replenishment";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(nullable = false)
    @Builder.Default
    private short priority = 5;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String source = "manual";

    @Column(name = "source_session_id")
    private UUID sourceSessionId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;
    @Column(name = "completed_at")                  private OffsetDateTime completedAt;
    @Column(name = "cancelled_at")                  private OffsetDateTime cancelledAt;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RefillTaskItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private RefillAssignment assignment;

    @PrePersist void prePersist() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate  void preUpdate()  { updatedAt = OffsetDateTime.now(); }

    public boolean isAssignable() { return "pending".equals(status); }
}
