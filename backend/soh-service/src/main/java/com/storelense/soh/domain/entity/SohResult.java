package com.storelense.soh.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "soh", name = "soh_results")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SohResult {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private SohSession session;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "total_products_counted", nullable = false)
    @Builder.Default private int totalProductsCounted = 0;

    @Column(name = "total_units_counted", nullable = false)
    @Builder.Default private int totalUnitsCounted = 0;

    @Column(name = "total_units_expected", nullable = false)
    @Builder.Default private int totalUnitsExpected = 0;

    @Column(name = "accuracy_pct", precision = 5, scale = 2)
    private BigDecimal accuracyPct;

    @Column(name = "variance_count", nullable = false)
    @Builder.Default private int varianceCount = 0;

    @Column(name = "overcount_items", nullable = false)
    @Builder.Default private int overcountItems = 0;

    @Column(name = "undercount_items", nullable = false)
    @Builder.Default private int undercountItems = 0;

    @Column(name = "result_generated_at", nullable = false)
    private OffsetDateTime resultGeneratedAt;

    @PrePersist void prePersist() { resultGeneratedAt = OffsetDateTime.now(); }
}
