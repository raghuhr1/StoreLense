package com.storelense.inventory.service;

import com.storelense.inventory.dto.BillItemDto;
import com.storelense.inventory.dto.BillLookupResponse;
import com.storelense.inventory.dto.BillRegistrationRequest;
import com.storelense.inventory.dto.BillSummaryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillService {

    private final JdbcClient jdbcClient;

    @Transactional
    public BillLookupResponse register(BillRegistrationRequest req) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // A fresh registration means a new sale — reset gate-check status even if
        // this bill_ref was previously released/flagged (e.g. ref reused after rollover).
        UUID billId = jdbcClient.sql("""
                INSERT INTO inventory.bills (bill_ref, store_id, cashier_id, total_items, created_at)
                VALUES (:billRef, CAST(:storeId AS uuid), CAST(:cashierId AS uuid), :totalItems, :now)
                ON CONFLICT (bill_ref, store_id) DO UPDATE
                    SET total_items     = EXCLUDED.total_items,
                        cashier_id      = EXCLUDED.cashier_id,
                        status          = 'PENDING',
                        gate_checked_at = NULL
                RETURNING id
                """)
                .param("billRef",     req.billRef())
                .param("storeId",     req.storeId().toString())
                .param("cashierId",   req.cashierId() != null ? req.cashierId().toString() : null)
                .param("totalItems",  req.items().size())
                .param("now",         now)
                .query(UUID.class).single();

        // Delete existing items then re-insert (idempotent re-registration)
        jdbcClient.sql("DELETE FROM inventory.bill_items WHERE bill_id = CAST(:id AS uuid)")
                .param("id", billId.toString())
                .update();

        for (BillItemDto item : req.items()) {
            jdbcClient.sql("""
                    INSERT INTO inventory.bill_items (bill_id, ean, product_name, qty, unit_price)
                    VALUES (CAST(:billId AS uuid), :ean, :productName, :qty, :unitPrice)
                    """)
                    .param("billId",      billId.toString())
                    .param("ean",         item.ean())
                    .param("productName", item.productName())
                    .param("qty",         item.qty())
                    .param("unitPrice",   item.unitPrice())
                    .update();
        }

        log.info("Bill registered: ref={} store={} items={}", req.billRef(), req.storeId(), req.items().size());
        return new BillLookupResponse(billId, req.billRef(), req.storeId(), now, fetchItems(billId), "PENDING", null);
    }

    @Transactional(readOnly = true)
    public BillLookupResponse lookup(String billRef, UUID storeId) {
        var bill = jdbcClient.sql("""
                SELECT id, bill_ref, store_id, created_at, status, gate_checked_at
                FROM inventory.bills
                WHERE UPPER(bill_ref) = UPPER(:billRef) AND store_id = CAST(:storeId AS uuid)
                """)
                .param("billRef",  billRef)
                .param("storeId",  storeId.toString())
                .query((rs, n) -> new Object[]{
                        rs.getObject("id", UUID.class),
                        rs.getString("bill_ref"),
                        rs.getObject("store_id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getString("status"),
                        rs.getObject("gate_checked_at", OffsetDateTime.class)
                })
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Bill '" + billRef + "' not found for this store"));

        UUID billId = (UUID) bill[0];

        return new BillLookupResponse(billId, (String) bill[1], (UUID) bill[2],
                (OffsetDateTime) bill[3], fetchItems(billId), (String) bill[4], (OffsetDateTime) bill[5]);
    }

    /**
     * Paged bill list for the dashboard. {@code status} filters on the exact bill
     * status; {@code pendingOnly} is the "never reached the guard app" shortcut and
     * keys off gate_checked_at rather than status, so bills left PENDING by a reset
     * registration are included.
     */
    @Transactional(readOnly = true)
    public Page<BillSummaryDto> list(UUID storeId,
                                     String status,
                                     boolean pendingOnly,
                                     OffsetDateTime from,
                                     OffsetDateTime to,
                                     String billRef,
                                     Pageable pageable) {

        String where = """
                WHERE (CAST(:storeId AS uuid)        IS NULL OR b.store_id  = CAST(:storeId AS uuid))
                  AND (CAST(:status  AS varchar)     IS NULL OR b.status    = CAST(:status AS varchar))
                  AND (CAST(:pendingOnly AS boolean) = FALSE  OR b.gate_checked_at IS NULL)
                  AND (CAST(:from AS timestamptz)    IS NULL OR b.created_at >= CAST(:from AS timestamptz))
                  AND (CAST(:to   AS timestamptz)    IS NULL OR b.created_at <= CAST(:to   AS timestamptz))
                  AND (CAST(:billRef AS varchar)     IS NULL OR UPPER(b.bill_ref) LIKE UPPER(CAST(:billRef AS varchar)))
                """;

        var binder = (java.util.function.Function<JdbcClient.StatementSpec, JdbcClient.StatementSpec>) spec -> spec
                .param("storeId",     storeId != null ? storeId.toString() : null)
                .param("status",      status)
                .param("pendingOnly", pendingOnly)
                .param("from",        from != null ? from.toString() : null)
                .param("to",          to   != null ? to.toString()   : null)
                .param("billRef",     billRef != null ? "%" + billRef + "%" : null);

        Long total = binder.apply(
                        jdbcClient.sql("SELECT COUNT(*) FROM inventory.bills b " + where))
                .query(Long.class).single();

        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        List<BillSummaryDto> rows = binder.apply(jdbcClient.sql("""
                        SELECT b.id, b.bill_ref, b.store_id, b.cashier_id, b.total_items,
                               b.status, b.created_at, b.gate_checked_at,
                               COALESCE((SELECT SUM(bi.qty * bi.unit_price)
                                         FROM inventory.bill_items bi
                                         WHERE bi.bill_id = b.id), 0) AS total_value
                        FROM inventory.bills b
                        """ + where + """
                        ORDER BY b.created_at DESC
                        LIMIT :limit OFFSET :offset
                        """))
                .param("limit",  pageable.getPageSize())
                .param("offset", pageable.getOffset())
                .query((rs, n) -> new BillSummaryDto(
                        rs.getObject("id", UUID.class),
                        rs.getString("bill_ref"),
                        rs.getObject("store_id", UUID.class),
                        rs.getObject("cashier_id", UUID.class),
                        rs.getInt("total_items"),
                        rs.getBigDecimal("total_value"),
                        rs.getString("status"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("gate_checked_at", OffsetDateTime.class)))
                .list();

        return new PageImpl<>(rows, pageable, total);
    }

    /**
     * Joins each bill line item to products.barcodes/products to resolve is_rfid_enabled.
     * Left join so an item resolves to null (unknown) rather than a wrong guess when
     * no barcode row exists for the EAN yet — a known onboarding data gap, not absence
     * of RFID tracking. Only a non-null false should be treated as "verify by barcode".
     */
    private List<BillItemDto> fetchItems(UUID billId) {
        return jdbcClient.sql("""
                SELECT bi.ean, bi.product_name, bi.qty, bi.unit_price, p.is_rfid_enabled, p.image_url
                FROM inventory.bill_items bi
                LEFT JOIN products.barcodes b ON UPPER(b.barcode_value) = UPPER(bi.ean)
                LEFT JOIN products.products p ON p.id = b.product_id
                WHERE bi.bill_id = CAST(:billId AS uuid)
                ORDER BY bi.ean
                """)
                .param("billId", billId.toString())
                .query((rs, n) -> new BillItemDto(
                        rs.getString("ean"),
                        rs.getString("product_name"),
                        rs.getInt("qty"),
                        rs.getBigDecimal("unit_price"),
                        (Boolean) rs.getObject("is_rfid_enabled"),
                        rs.getString("image_url")
                ))
                .list();
    }
}
