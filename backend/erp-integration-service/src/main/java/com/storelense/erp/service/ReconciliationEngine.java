package com.storelense.erp.service;

import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.erp.domain.entity.*;
import com.storelense.erp.domain.repository.*;
import com.storelense.erp.service.SohServiceClient.SohSessionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationEngine {

    private final CcReconciliationRepository     reconciliationRepository;
    private final CcReconciliationItemRepository itemRepository;
    private final ErpSohSnapshotEpcRepository    snapshotEpcRepository;
    private final ErpImportBatchRepository       batchRepository;
    private final SohServiceClient               sohServiceClient;
    private final JdbcClient                     jdbcClient;

    // Self-injection so @Transactional on persistReconciliation is intercepted
    @Lazy @Autowired
    private ReconciliationEngine self;

    // ── Public API ─────────────────────────────────────────────────────────

    public CcReconciliation reconcile(UUID sessionId) {
        // Phase 1: reads + HTTP calls outside the write transaction
        SohSessionInfo session = sohServiceClient.getSession(sessionId);
        List<String> scannedEpcs = sohServiceClient.getSessionEpcs(sessionId);

        Optional<ErpImportBatch> batchOpt = batchRepository
                .findTopByStoreIdAndStatusOrderByCreatedAtDesc(session.storeId(), "COMPLETED");

        if (batchOpt.isPresent()) {
            List<ErpSohSnapshotEpc> snapEpcs = snapshotEpcRepository
                    .findByBatchAndZone(batchOpt.get().getId(), session.zoneRegion());

            if (!snapEpcs.isEmpty()) {
                // ERP-driven: expected set comes from the latest import batch
                return self.persistReconciliation(
                        sessionId, batchOpt.get(), session.storeId(), scannedEpcs, snapEpcs);
            }

            log.info("ERP batch {} has no resolved EPCs for session {} — falling back to system-driven",
                    batchOpt.get().getId(), sessionId);
        } else {
            log.info("No completed ERP batch for store {} — using system-driven reconciliation for session {}",
                    session.storeId(), sessionId);
        }

        // System-driven: expected set = non-sold EPCs tracked in epc_registry
        return self.persistSystemDrivenReconciliation(sessionId, session.storeId(), scannedEpcs);
    }

    public Page<CcReconciliation> listByStore(UUID storeId, Pageable pageable) {
        return reconciliationRepository.findByStoreIdOrderByRunAtDesc(storeId, pageable);
    }

    public CcReconciliation getLatestResult(UUID sessionId) {
        return reconciliationRepository.findTopBySessionIdOrderByRunAtDesc(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CcReconciliation", sessionId));
    }

    public List<CcReconciliationItem> getItems(UUID reconciliationId) {
        return itemRepository.findByReconciliation_Id(reconciliationId);
    }

    // ── Transactional persist (called via proxy) ───────────────────────────

    @SuppressWarnings("null")
    @Transactional
    public CcReconciliation persistReconciliation(UUID sessionId, ErpImportBatch batch,
                                                   UUID storeId,
                                                   List<String> scannedEpcList,
                                                   List<ErpSohSnapshotEpc> snapEpcs) {
        Set<String> expected = snapEpcs.stream()
                .map(ErpSohSnapshotEpc::getEpc)
                .collect(Collectors.toSet());
        Set<String> scanned = new HashSet<>(scannedEpcList);

        Map<String, String> epcToEan = snapEpcs.stream()
                .collect(Collectors.toMap(ErpSohSnapshotEpc::getEpc,
                        e -> e.getSnapshot().getEan(),
                        (a, b) -> a));

        Set<String> matched = new HashSet<>(scanned);
        matched.retainAll(expected);

        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(scanned);

        Set<String> extra = new HashSet<>(scanned);
        extra.removeAll(expected);

        BigDecimal accuracy = expected.isEmpty() ? BigDecimal.valueOf(100)
                : BigDecimal.valueOf(100.0 * matched.size() / expected.size())
                        .setScale(2, RoundingMode.HALF_UP);

        CcReconciliation recon = reconciliationRepository.save(CcReconciliation.builder()
                .sessionId(sessionId)
                .batchId(batch.getId())
                .storeId(storeId)
                .totalExpected(expected.size())
                .totalScanned(scanned.size())
                .matchedCount(matched.size())
                .missingCount(missing.size())
                .extraCount(extra.size())
                .accuracyPct(accuracy)
                .status("COMPLETED")
                .build());

        List<CcReconciliationItem> items = new ArrayList<>();
        matched.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MATCH",   1, 1)));
        missing.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MISSING", 1, 0)));
        extra.forEach(  epc -> items.add(item(recon, epc, null,              "EXTRA",   0, 1)));
        itemRepository.saveAll(items);

        // Best-effort accuracy sync to soh_results (no-op if session not yet completed)
        jdbcClient.sql("""
                UPDATE soh.soh_results
                SET accuracy_pct = :accuracy
                WHERE session_id = :sessionId::uuid
                """)
                .param("accuracy", accuracy)
                .param("sessionId", sessionId.toString())
                .update();

        log.info("Reconciliation {} complete: session={} matched={} missing={} extra={} accuracy={}%",
                recon.getId(), sessionId, matched.size(), missing.size(), extra.size(), accuracy);
        return recon;
    }

    // ── System-driven reconciliation (no ERP batch required) ──────────────
    // Expected set = all EPCs in epc_registry that have not been sold/damaged/transferred.
    // This uses the system's own live tracking as the source of truth rather than the ERP.

    @SuppressWarnings("null")
    @Transactional
    public CcReconciliation persistSystemDrivenReconciliation(UUID sessionId, UUID storeId,
                                                               List<String> scannedEpcList) {
        // Single query: expected EPCs + their primary EAN via cross-schema JOIN
        record EpcEan(String epc, String ean) {}
        List<EpcEan> expectedRows = jdbcClient.sql("""
                SELECT er.epc,
                       b.barcode_value AS ean
                FROM inventory.epc_registry er
                LEFT JOIN products.epc_tags  et ON et.epc        = er.epc
                                                AND et.is_active  = true
                LEFT JOIN products.barcodes  b  ON b.product_id  = et.product_id
                                                AND b.is_primary  = true
                WHERE er.store_id = :storeId
                  AND er.status NOT IN ('sold', 'damaged', 'transferred')
                """)
                .param("storeId", storeId)
                .query((rs, n) -> new EpcEan(rs.getString("epc"), rs.getString("ean")))
                .list();

        Map<String, String> epcToEan = expectedRows.stream()
                .collect(Collectors.toMap(EpcEan::epc, r -> r.ean() != null ? r.ean() : "",
                        (a, b) -> a));

        Set<String> expected = epcToEan.keySet();
        Set<String> scanned  = new HashSet<>(scannedEpcList);

        Set<String> matched = new HashSet<>(scanned);
        matched.retainAll(expected);

        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(scanned);

        Set<String> extra = new HashSet<>(scanned);
        extra.removeAll(expected);

        BigDecimal accuracy = expected.isEmpty() ? BigDecimal.valueOf(100)
                : BigDecimal.valueOf(100.0 * matched.size() / expected.size())
                        .setScale(2, RoundingMode.HALF_UP);

        CcReconciliation recon = reconciliationRepository.save(CcReconciliation.builder()
                .sessionId(sessionId)
                .batchId(null)
                .mode("SYSTEM_DRIVEN")
                .storeId(storeId)
                .totalExpected(expected.size())
                .totalScanned(scanned.size())
                .matchedCount(matched.size())
                .missingCount(missing.size())
                .extraCount(extra.size())
                .accuracyPct(accuracy)
                .status("COMPLETED")
                .build());

        List<CcReconciliationItem> items = new ArrayList<>();
        matched.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MATCH",   1, 1)));
        missing.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MISSING", 1, 0)));
        extra.forEach(  epc -> items.add(item(recon, epc, null,              "EXTRA",   0, 1)));
        itemRepository.saveAll(items);

        jdbcClient.sql("""
                UPDATE soh.soh_results
                SET accuracy_pct = :accuracy
                WHERE session_id = :sessionId::uuid
                """)
                .param("accuracy", accuracy)
                .param("sessionId", sessionId.toString())
                .update();

        log.info("System-driven reconciliation {} complete: session={} matched={} missing={} extra={} accuracy={}%",
                recon.getId(), sessionId, matched.size(), missing.size(), extra.size(), accuracy);
        return recon;
    }

    private CcReconciliationItem item(CcReconciliation recon, String epc, String ean,
                                      String status, int expectedQty, int scannedQty) {
        return CcReconciliationItem.builder()
                .reconciliation(recon)
                .epc(epc)
                .ean(ean)
                .status(status)
                .expectedQty(expectedQty)
                .scannedQty(scannedQty)
                .build();
    }
}
