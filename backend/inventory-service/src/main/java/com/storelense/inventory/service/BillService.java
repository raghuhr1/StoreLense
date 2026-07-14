package com.storelense.inventory.service;

import com.storelense.inventory.dto.BillItemDto;
import com.storelense.inventory.dto.BillLookupResponse;
import com.storelense.inventory.dto.BillRegistrationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        return new BillLookupResponse(billId, req.billRef(), req.storeId(), now, req.items(), "PENDING", null);
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

        List<BillItemDto> items = jdbcClient.sql("""
                SELECT ean, product_name, qty, unit_price
                FROM inventory.bill_items
                WHERE bill_id = CAST(:billId AS uuid)
                ORDER BY ean
                """)
                .param("billId", billId.toString())
                .query((rs, n) -> new BillItemDto(
                        rs.getString("ean"),
                        rs.getString("product_name"),
                        rs.getInt("qty"),
                        rs.getBigDecimal("unit_price")
                ))
                .list();

        return new BillLookupResponse(billId, (String) bill[1], (UUID) bill[2],
                (OffsetDateTime) bill[3], items, (String) bill[4], (OffsetDateTime) bill[5]);
    }
}
