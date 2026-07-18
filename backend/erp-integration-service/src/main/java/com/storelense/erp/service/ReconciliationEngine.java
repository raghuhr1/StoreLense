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
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationEngine {

    private final CcReconciliationRepository     reconciliationRepository;
    private final CcReconciliationItemRepository itemRepository;
    private final ErpImportBatchRepository       batchRepository;
    private final SohServiceClient               sohServiceClient;
    private final JdbcClient                     jdbcClient;

    // Self-injection so @Transactional on persist* methods is intercepted
    @Lazy @Autowired
    private ReconciliationEngine self;

    // ── Single-session reconciliation ──────────────────────────────────────

    public CcReconciliation reconcile(UUID sessionId) {
        List<CcReconciliation> existing = reconciliationRepository.findBySessionIdOrderByRunAtDesc(sessionId);
        if (!existing.isEmpty()) {
            existing.forEach(r -> {
                itemRepository.deleteAll(itemRepository.findByReconciliation_Id(r.getId()));
                reconciliationRepository.delete(r);
            });
            log.info("Removed {} stale reconciliation record(s) for session {} before re-run",
                    existing.size(), sessionId);
        }

        SohSessionInfo session = sohServiceClient.getSession(sessionId);
        List<String> scannedEpcs = sohServiceClient.getSessionEpcs(sessionId);

        Optional<ErpImportBatch> batchOpt = batchRepository
                .findTopByStoreIdAndStatusOrderByCreatedAtDesc(session.storeId(), "COMPLETED");

        if (batchOpt.isPresent()) {
            Map<String, Integer> expectedQtyByEan = resolveExpectedQuantities(batchOpt.get().getId());

            if (!expectedQtyByEan.isEmpty()) {
                return self.persistReconciliation(
                        sessionId, null, batchOpt.get(), session, scannedEpcs);
            }

            log.info("ERP batch {} has no expected quantities for session {} zone={} — falling back to system-driven",
                    batchOpt.get().getId(), sessionId, session.zoneRegion());
        } else {
            log.info("No completed ERP batch for store {} — using system-driven reconciliation for session {}",
                    session.storeId(), sessionId);
        }

        return self.persistSystemDrivenReconciliation(sessionId, null, session, scannedEpcs);
    }

    // ── Cycle-count reconciliation (multi-session) ─────────────────────────

    public CcReconciliation reconcileByCount(UUID cycleCountId) {
        // Remove prior results for this cycle count (re-run idempotency)
        reconciliationRepository.findByCycleCountIdOrderByRunAtDesc(cycleCountId).forEach(r -> {
            itemRepository.deleteAll(itemRepository.findByReconciliation_Id(r.getId()));
            reconciliationRepository.delete(r);
        });

        List<SohSessionInfo> sessions = sohServiceClient.getSessionsByCount(cycleCountId);
        List<SohSessionInfo> countable = sessions.stream()
                .filter(s -> "completed".equals(s.status())
                          || "uploaded".equals(s.status())
                          || "reconciled".equals(s.status()))
                .toList();

        if (countable.isEmpty()) {
            throw new IllegalStateException(
                    "No completed sessions found for cycle count " + cycleCountId);
        }

        UUID storeId = countable.get(0).storeId();

        // Merge EPCs across sessions, tagging each with its source location
        // epc → locationCode of the session it was first found in
        Map<String, String> epcToLocation = new LinkedHashMap<>();
        Map<String, String> epcToSection  = new LinkedHashMap<>();

        for (SohSessionInfo session : countable) {
            List<String> epcs = sohServiceClient.getSessionEpcs(session.id());
            for (String epc : epcs) {
                epcToLocation.putIfAbsent(epc, session.locationCode());
                epcToSection.putIfAbsent(epc,  session.sectionCode());
            }
        }

        Optional<ErpImportBatch> batchOpt = batchRepository
                .findTopByStoreIdAndStatusOrderByCreatedAtDesc(storeId, "COMPLETED");

        if (batchOpt.isPresent()) {
            Map<String, String> epcToEan = resolveExpectedEpcs(
                    batchOpt.get().getId(), storeId, null);   // all zones for count
            if (!epcToEan.isEmpty()) {
                return self.persistCountReconciliation(
                        cycleCountId, storeId, batchOpt.get(),
                        epcToLocation, epcToSection, epcToEan);
            }
        }

        return self.persistCountSystemDrivenReconciliation(
                cycleCountId, storeId, epcToLocation, epcToSection);
    }

    // ── Approval ───────────────────────────────────────────────────────────

    @Transactional
    public CcReconciliation approve(UUID reconciliationId, UUID reviewerId) {
        CcReconciliation recon = reconciliationRepository.findById(reconciliationId)
                .orElseThrow(() -> new ResourceNotFoundException("CcReconciliation", reconciliationId));

        if (!"COMPLETED".equals(recon.getStatus()) && !"PENDING_APPROVAL".equals(recon.getStatus())) {
            throw new IllegalStateException(
                    "Reconciliation " + reconciliationId + " cannot be approved from status " + recon.getStatus());
        }

        recon.setReviewerId(reviewerId);
        recon.setApprovedAt(OffsetDateTime.now());
        recon.setStatus("APPROVED");
        return reconciliationRepository.save(recon);
    }

    // ── Query helpers ──────────────────────────────────────────────────────

    public Page<CcReconciliation> listByStore(UUID storeId, Pageable pageable) {
        return reconciliationRepository.findByStoreIdOrderByRunAtDesc(storeId, pageable);
    }

    public CcReconciliation getLatestResult(UUID sessionId) {
        return reconciliationRepository.findTopBySessionIdOrderByRunAtDesc(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CcReconciliation", sessionId));
    }

    public CcReconciliation getLatestCountResult(UUID cycleCountId) {
        return reconciliationRepository.findTopByCycleCountIdOrderByRunAtDesc(cycleCountId)
                .orElseThrow(() -> new ResourceNotFoundException("CcReconciliation (by count)", cycleCountId));
    }

    public List<CcReconciliationItem> getItems(UUID reconciliationId) {
        return itemRepository.findByReconciliation_Id(reconciliationId);
    }

    public List<CcReconciliationItem> getItemsByLocation(UUID reconciliationId, String locationCode) {
        return itemRepository.findByReconciliation_Id(reconciliationId).stream()
                .filter(i -> locationCode == null || locationCode.equals(i.getLocationCode()))
                .toList();
    }

    // ── Transactional persist — single session ────────────────────────────

    @SuppressWarnings("null")
    @Transactional
    public CcReconciliation persistReconciliation(UUID sessionId, UUID cycleCountId,
                                                   ErpImportBatch batch,
                                                   SohSessionInfo session,
                                                   List<String> scannedEpcList) {
        // "Expected" must come from the ERP's actual quantity per EAN, not from which EPCs
        // happen to already be scanned/tagged — otherwise nothing can ever be "missing",
        // since an EPC can't enter the expected set until it's already been found.
        Map<String, Integer> expectedQtyByEan = resolveExpectedQuantities(batch.getId());
        Map<String, String>  scannedEpcToEan  = resolveScannedEans(session.storeId(), scannedEpcList);

        Map<String, List<String>> scannedByEan = new HashMap<>();
        for (String epc : scannedEpcList) {
            String ean = scannedEpcToEan.getOrDefault(epc, "");
            scannedByEan.computeIfAbsent(ean, k -> new ArrayList<>()).add(epc);
        }

        Set<String> allEans = new HashSet<>(expectedQtyByEan.keySet());
        allEans.addAll(scannedByEan.keySet());
        allEans.remove("");

        // Build item rows as plain tuples first — CcReconciliationItem needs a persisted
        // CcReconciliation to attach to, which doesn't exist until totals below are saved.
        record ItemRow(String epc, String ean, String status, int expectedQty, int scannedQty) {}
        List<ItemRow> rows = new ArrayList<>();
        int matchedTotal = 0, missingTotal = 0, extraTotal = 0;
        int totalExpected = expectedQtyByEan.values().stream().mapToInt(Integer::intValue).sum();

        for (String ean : allEans) {
            int expectedQty = expectedQtyByEan.getOrDefault(ean, 0);
            List<String> scannedForEan = scannedByEan.getOrDefault(ean, List.of());
            int matchedQty = Math.min(expectedQty, scannedForEan.size());
            int missingQty = Math.max(0, expectedQty - scannedForEan.size());

            for (int i = 0; i < matchedQty; i++) {
                rows.add(new ItemRow(scannedForEan.get(i), ean, "MATCH", 1, 1));
            }
            for (int i = matchedQty; i < scannedForEan.size(); i++) {
                rows.add(new ItemRow(scannedForEan.get(i), ean, "EXTRA", 0, 1));
            }
            for (int i = 0; i < missingQty; i++) {
                // No physical EPC has ever been scanned for this unit — synthesize a
                // clearly-labeled placeholder since the epc column is non-nullable.
                rows.add(new ItemRow("EXPECTED:" + ean + ":" + (i + 1), ean, "MISSING", 1, 0));
            }
            matchedTotal += matchedQty;
            missingTotal += missingQty;
            extraTotal   += scannedForEan.size() - matchedQty;
        }

        // Scans that resolved to no product/EAN at all — genuinely unexpected reads.
        List<String> unresolvedScans = scannedByEan.getOrDefault("", List.of());
        for (String epc : unresolvedScans) {
            rows.add(new ItemRow(epc, null, "EXTRA", 0, 1));
        }
        extraTotal += unresolvedScans.size();

        int totalScanned = scannedEpcList.size();
        BigDecimal accuracy = totalExpected == 0
                ? (totalScanned == 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO)
                : BigDecimal.valueOf(100.0 * matchedTotal / totalExpected)
                        .setScale(2, RoundingMode.HALF_UP);

        String loc = session.locationCode();
        boolean isFloor = "SALES_FLOOR".equals(loc);
        boolean isBack  = "BACKROOM".equals(loc);

        CcReconciliation recon = reconciliationRepository.save(CcReconciliation.builder()
                .sessionId(sessionId)
                .cycleCountId(cycleCountId)
                .batchId(batch.getId())
                .storeId(session.storeId())
                .totalExpected(totalExpected)
                .totalScanned(totalScanned)
                .matchedCount(matchedTotal)
                .missingCount(missingTotal)
                .extraCount(extraTotal)
                .accuracyPct(accuracy)
                .floorExpected(isFloor ? totalExpected : 0)
                .floorScanned(isFloor ? totalScanned : 0)
                .floorMissing(isFloor ? missingTotal : 0)
                .backroomExpected(isBack ? totalExpected : 0)
                .backroomScanned(isBack ? totalScanned : 0)
                .backroomMissing(isBack ? missingTotal : 0)
                .status("COMPLETED")
                .build());

        List<CcReconciliationItem> items = rows.stream()
                .map(r -> item(recon, r.epc(), r.ean(), r.status(), r.expectedQty(), r.scannedQty(), loc, session.sectionCode()))
                .toList();
        itemRepository.saveAll(items);

        syncAccuracy(sessionId, accuracy);

        log.info("Reconciliation {} complete: session={} location={} matched={} missing={} extra={} accuracy={}%",
                recon.getId(), sessionId, loc, matchedTotal, missingTotal, extraTotal, accuracy);
        return recon;
    }

    @SuppressWarnings("null")
    @Transactional
    public CcReconciliation persistSystemDrivenReconciliation(UUID sessionId, UUID cycleCountId,
                                                               SohSessionInfo session,
                                                               List<String> scannedEpcList) {
        record EpcEan(String epc, String ean) {}
        List<EpcEan> expectedRows = jdbcClient.sql("""
                SELECT er.epc, b.barcode_value AS ean
                FROM inventory.epc_registry er
                LEFT JOIN products.epc_tags et ON et.epc = er.epc AND et.is_active = true
                LEFT JOIN products.barcodes  b  ON b.product_id = et.product_id AND b.is_primary = true
                WHERE er.store_id = :storeId
                  AND er.status NOT IN ('sold','damaged','transferred')
                """)
                .param("storeId", session.storeId())
                .query((rs, n) -> new EpcEan(rs.getString("epc"), rs.getString("ean")))
                .list();

        Map<String, String> epcToEan = expectedRows.stream()
                .collect(Collectors.toMap(EpcEan::epc, r -> r.ean() != null ? r.ean() : "",
                        (a, b) -> a));

        Set<String> expected = epcToEan.keySet();
        Set<String> scanned  = new HashSet<>(scannedEpcList);
        Set<String> matched  = new HashSet<>(scanned); matched.retainAll(expected);
        Set<String> missing  = new HashSet<>(expected); missing.removeAll(scanned);
        Set<String> extra    = new HashSet<>(scanned);  extra.removeAll(expected);

        BigDecimal accuracy = expected.isEmpty()
                ? (scanned.isEmpty() ? BigDecimal.valueOf(100) : BigDecimal.ZERO)
                : BigDecimal.valueOf(100.0 * matched.size() / expected.size())
                        .setScale(2, RoundingMode.HALF_UP);

        String loc = session.locationCode();
        boolean isFloor = "SALES_FLOOR".equals(loc);
        boolean isBack  = "BACKROOM".equals(loc);

        CcReconciliation recon = reconciliationRepository.save(CcReconciliation.builder()
                .sessionId(sessionId)
                .cycleCountId(cycleCountId)
                .batchId(null)
                .mode("SYSTEM_DRIVEN")
                .storeId(session.storeId())
                .totalExpected(expected.size())
                .totalScanned(scanned.size())
                .matchedCount(matched.size())
                .missingCount(missing.size())
                .extraCount(extra.size())
                .accuracyPct(accuracy)
                .floorExpected(isFloor ? expected.size() : 0)
                .floorScanned(isFloor ? scanned.size() : 0)
                .floorMissing(isFloor ? missing.size() : 0)
                .backroomExpected(isBack ? expected.size() : 0)
                .backroomScanned(isBack ? scanned.size() : 0)
                .backroomMissing(isBack ? missing.size() : 0)
                .status("COMPLETED")
                .build());

        List<CcReconciliationItem> items = new ArrayList<>();
        matched.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MATCH",   1, 1, loc, session.sectionCode())));
        missing.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MISSING", 1, 0, loc, session.sectionCode())));
        extra.forEach(  epc -> items.add(item(recon, epc, null,              "EXTRA",   0, 1, loc, session.sectionCode())));
        itemRepository.saveAll(items);

        syncAccuracy(sessionId, accuracy);

        log.info("System-driven reconciliation {} complete: session={} location={} matched={} missing={} extra={} accuracy={}%",
                recon.getId(), sessionId, loc, matched.size(), missing.size(), extra.size(), accuracy);
        return recon;
    }

    // ── Transactional persist — cycle count (multi-session) ───────────────

    @SuppressWarnings("null")
    @Transactional
    public CcReconciliation persistCountReconciliation(UUID cycleCountId, UUID storeId,
                                                        ErpImportBatch batch,
                                                        Map<String, String> epcToLocation,
                                                        Map<String, String> epcToSection,
                                                        Map<String, String> epcToEan) {
        Set<String> expected = epcToEan.keySet();

        Set<String> scannedAll = epcToLocation.keySet();
        Set<String> matched    = new HashSet<>(scannedAll); matched.retainAll(expected);
        Set<String> missing    = new HashSet<>(expected);   missing.removeAll(scannedAll);
        Set<String> extra      = new HashSet<>(scannedAll); extra.removeAll(expected);

        // Per-location breakdown
        Set<String> floorScannedSet = scannedAll.stream()
                .filter(e -> "SALES_FLOOR".equals(epcToLocation.get(e))).collect(Collectors.toSet());
        Set<String> backScannedSet  = scannedAll.stream()
                .filter(e -> "BACKROOM".equals(epcToLocation.get(e))).collect(Collectors.toSet());

        // An EPC is "floor missing" if it was expected but not found in any floor session
        int floorMissing = (int) missing.stream()
                .filter(e -> !floorScannedSet.contains(e)).count();
        int backMissing  = (int) missing.stream()
                .filter(e -> !backScannedSet.contains(e)).count();

        BigDecimal accuracy = expected.isEmpty()
                ? (scannedAll.isEmpty() ? BigDecimal.valueOf(100) : BigDecimal.ZERO)
                : BigDecimal.valueOf(100.0 * matched.size() / expected.size())
                        .setScale(2, RoundingMode.HALF_UP);

        CcReconciliation recon = reconciliationRepository.save(CcReconciliation.builder()
                .cycleCountId(cycleCountId)
                .batchId(batch.getId())
                .storeId(storeId)
                .totalExpected(expected.size())
                .totalScanned(scannedAll.size())
                .matchedCount(matched.size())
                .missingCount(missing.size())
                .extraCount(extra.size())
                .accuracyPct(accuracy)
                .floorExpected(expected.size())
                .floorScanned(floorScannedSet.size())
                .floorMissing(floorMissing)
                .backroomExpected(expected.size())
                .backroomScanned(backScannedSet.size())
                .backroomMissing(backMissing)
                .status("PENDING_APPROVAL")
                .build());

        List<CcReconciliationItem> items = new ArrayList<>();
        matched.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MATCH",
                1, 1, epcToLocation.get(epc), epcToSection.get(epc))));
        missing.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MISSING",
                1, 0, null, null)));
        extra.forEach(  epc -> items.add(item(recon, epc, null,              "EXTRA",
                0, 1, epcToLocation.get(epc), epcToSection.get(epc))));
        itemRepository.saveAll(items);

        log.info("Count reconciliation {} complete: cycleCount={} matched={} missing={} extra={} accuracy={}%",
                recon.getId(), cycleCountId, matched.size(), missing.size(), extra.size(), accuracy);
        return recon;
    }

    @SuppressWarnings("null")
    @Transactional
    public CcReconciliation persistCountSystemDrivenReconciliation(UUID cycleCountId, UUID storeId,
                                                                    Map<String, String> epcToLocation,
                                                                    Map<String, String> epcToSection) {
        record EpcEan(String epc, String ean) {}
        List<EpcEan> expectedRows = jdbcClient.sql("""
                SELECT er.epc, b.barcode_value AS ean
                FROM inventory.epc_registry er
                LEFT JOIN products.epc_tags et ON et.epc = er.epc AND et.is_active = true
                LEFT JOIN products.barcodes  b  ON b.product_id = et.product_id AND b.is_primary = true
                WHERE er.store_id = :storeId
                  AND er.status NOT IN ('sold','damaged','transferred')
                """)
                .param("storeId", storeId)
                .query((rs, n) -> new EpcEan(rs.getString("epc"), rs.getString("ean")))
                .list();

        Map<String, String> epcToEan = expectedRows.stream()
                .collect(Collectors.toMap(EpcEan::epc, r -> r.ean() != null ? r.ean() : "",
                        (a, b) -> a));

        Set<String> expected   = epcToEan.keySet();
        Set<String> scannedAll = epcToLocation.keySet();
        Set<String> matched    = new HashSet<>(scannedAll); matched.retainAll(expected);
        Set<String> missing    = new HashSet<>(expected);   missing.removeAll(scannedAll);
        Set<String> extra      = new HashSet<>(scannedAll); extra.removeAll(expected);

        Set<String> floorScannedSet = scannedAll.stream()
                .filter(e -> "SALES_FLOOR".equals(epcToLocation.get(e))).collect(Collectors.toSet());
        Set<String> backScannedSet  = scannedAll.stream()
                .filter(e -> "BACKROOM".equals(epcToLocation.get(e))).collect(Collectors.toSet());

        int floorMissing = (int) missing.stream().filter(e -> !floorScannedSet.contains(e)).count();
        int backMissing  = (int) missing.stream().filter(e -> !backScannedSet.contains(e)).count();

        BigDecimal accuracy = expected.isEmpty()
                ? (scannedAll.isEmpty() ? BigDecimal.valueOf(100) : BigDecimal.ZERO)
                : BigDecimal.valueOf(100.0 * matched.size() / expected.size())
                        .setScale(2, RoundingMode.HALF_UP);

        CcReconciliation recon = reconciliationRepository.save(CcReconciliation.builder()
                .cycleCountId(cycleCountId)
                .batchId(null)
                .mode("SYSTEM_DRIVEN")
                .storeId(storeId)
                .totalExpected(expected.size())
                .totalScanned(scannedAll.size())
                .matchedCount(matched.size())
                .missingCount(missing.size())
                .extraCount(extra.size())
                .accuracyPct(accuracy)
                .floorExpected(expected.size())
                .floorScanned(floorScannedSet.size())
                .floorMissing(floorMissing)
                .backroomExpected(expected.size())
                .backroomScanned(backScannedSet.size())
                .backroomMissing(backMissing)
                .status("PENDING_APPROVAL")
                .build());

        List<CcReconciliationItem> items = new ArrayList<>();
        matched.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MATCH",
                1, 1, epcToLocation.get(epc), epcToSection.get(epc))));
        missing.forEach(epc -> items.add(item(recon, epc, epcToEan.get(epc), "MISSING",
                1, 0, null, null)));
        extra.forEach(  epc -> items.add(item(recon, epc, null,              "EXTRA",
                0, 1, epcToLocation.get(epc), epcToSection.get(epc))));
        itemRepository.saveAll(items);

        log.info("Count system-driven reconciliation {} complete: cycleCount={} matched={} missing={} extra={} accuracy={}%",
                recon.getId(), cycleCountId, matched.size(), missing.size(), extra.size(), accuracy);
        return recon;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Resolves expected EPCs for a batch by product/EAN match — NOT by relying on
     * erp_soh_snapshot_epcs (only pre-linked EPCs, empty right after a fresh ERP import) as
     * findByBatchAndZone did.
     *
     * Not zone-scoped: the ERP feed is EAN/qty/storeId only, with no per-zone breakdown, so
     * every product expected at the store is expected regardless of which single session is
     * being reconciled. Per-session reconcile() calls this with the session's own zone purely
     * as an unused parameter for API-shape consistency with reconcileByCount (zoneRegion is
     * accepted but ignored) — the authoritative comparison for a zone-picker workflow is the
     * COMBINED reconcileByCount() across all zone sessions in a cycle count, which is why
     * SohSessionService.startSession auto-groups Sales Floor + Back Room sessions together.
     */
    private Map<String, String> resolveExpectedEpcs(UUID batchId, UUID storeId, String zoneRegion) {
        record EpcEan(String epc, String ean) {}
        return jdbcClient.sql("""
                SELECT DISTINCT er.epc, s.ean
                FROM inventory.epc_registry er
                JOIN products.epc_tags et ON et.epc = er.epc AND et.is_active = true
                JOIN products.barcodes b  ON b.product_id = et.product_id
                JOIN erp.erp_soh_snapshot s ON UPPER(b.barcode_value) = UPPER(s.ean)
                WHERE s.batch_id = :batchId
                  AND er.store_id = :storeId
                  AND er.status NOT IN ('sold', 'damaged', 'transferred')
                """)
                .param("batchId", batchId)
                .param("storeId", storeId)
                .query((rs, n) -> new EpcEan(rs.getString("epc"), rs.getString("ean")))
                .list().stream()
                .collect(Collectors.toMap(EpcEan::epc, EpcEan::ean, (a, b) -> a));
    }

    /**
     * The ERP's true expected quantity per EAN for a batch — summed across any duplicate
     * rows for the same EAN. This is the actual source of truth for "expected", independent
     * of whether any physical EPC has been scanned/tagged for that product yet.
     */
    private Map<String, Integer> resolveExpectedQuantities(UUID batchId) {
        record EanQty(String ean, int qty) {}
        return jdbcClient.sql("""
                SELECT ean, SUM(expected_qty) AS qty
                FROM erp.erp_soh_snapshot
                WHERE batch_id = :batchId
                GROUP BY ean
                """)
                .param("batchId", batchId)
                .query((rs, n) -> new EanQty(rs.getString("ean"), rs.getInt("qty")))
                .list().stream()
                .collect(Collectors.toMap(EanQty::ean, EanQty::qty));
    }

    /** Resolves each scanned EPC (if any) to its product's primary EAN, for grouping by product. */
    private Map<String, String> resolveScannedEans(UUID storeId, List<String> scannedEpcs) {
        if (scannedEpcs.isEmpty()) return Map.of();
        record EpcEan(String epc, String ean) {}
        return jdbcClient.sql("""
                SELECT er.epc, b.barcode_value AS ean
                FROM inventory.epc_registry er
                JOIN products.epc_tags et ON et.epc = er.epc AND et.is_active = true
                JOIN products.barcodes  b  ON b.product_id = et.product_id AND b.is_primary = true
                WHERE er.store_id = :storeId
                  AND er.epc IN (:epcs)
                """)
                .param("storeId", storeId)
                .param("epcs", scannedEpcs)
                .query((rs, n) -> new EpcEan(rs.getString("epc"), rs.getString("ean")))
                .list().stream()
                .collect(Collectors.toMap(EpcEan::epc, EpcEan::ean, (a, b) -> a));
    }

    private CcReconciliationItem item(CcReconciliation recon, String epc, String ean,
                                      String status, int expectedQty, int scannedQty,
                                      String locationCode, String sectionCode) {
        return CcReconciliationItem.builder()
                .reconciliation(recon)
                .epc(epc)
                .ean(ean)
                .status(status)
                .expectedQty(expectedQty)
                .scannedQty(scannedQty)
                .locationCode(locationCode)
                .sectionCode(sectionCode)
                .build();
    }

    private void syncAccuracy(UUID sessionId, BigDecimal accuracy) {
        if (sessionId == null) return;
        jdbcClient.sql("""
                UPDATE soh.soh_results
                SET accuracy_pct = :accuracy
                WHERE session_id = :sessionId::uuid
                """)
                .param("accuracy", accuracy)
                .param("sessionId", sessionId.toString())
                .update();
    }
}
