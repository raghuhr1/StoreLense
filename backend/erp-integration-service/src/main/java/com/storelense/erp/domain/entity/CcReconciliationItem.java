package com.storelense.erp.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(schema = "erp", name = "cc_reconciliation_items")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CcReconciliationItem {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconciliation_id", nullable = false)
    private CcReconciliation reconciliation;

    @Column(nullable = false, length = 100)
    private String epc;

    @Column(length = 30)
    private String ean;

    /** MATCH | MISSING | EXTRA */
    @Column(nullable = false, length = 10)
    private String status;

    @Column(name = "expected_qty", nullable = false)
    @Builder.Default private int expectedQty = 0;

    @Column(name = "scanned_qty", nullable = false)
    @Builder.Default private int scannedQty = 0;

    /** SALES_FLOOR or BACKROOM — where this EPC was found (or expected). */
    @Column(name = "location_code", length = 20)
    private String locationCode;

    /** MENS | WOMENS | KIDS | FOOTWEAR | ACCESSORIES — null for BACKROOM items. */
    @Column(name = "section_code", length = 20)
    private String sectionCode;
}
