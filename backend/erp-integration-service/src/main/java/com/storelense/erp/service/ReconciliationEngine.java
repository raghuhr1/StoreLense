package com.storelense.erp.service;

import com.storelense.common.exception.BusinessException;
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
import org.springframework.http.HttpStatus;
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

        ErpImportBatch batch = batchRepository
                .findTopByStoreIdAndStatusOrderByCreatedAtDesc(session.storeId(), "COMPLETED")
                .orElseThrow(() -> new BusinessException("NO_COMPLETED_BATCH",
                        "No completed ERP import batch found for store " + session.storeId(),
                        HttpStatus.CONFLICT));

        List<ErpSohSnapshotEpc> snapEpcs = snapshotEpcRepository
                .findByBatchAndZone(batch.getId(), session.zoneRegion());

        // Phase 2: compute sets, persist — transactional
        return self.persistReconciliation(sessionId, batch, session.storeId(), scannedEpcs, snapEpcs);
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
