package com.storelense.soh.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.event.ErpImportCompletedEvent;
import com.storelense.common.event.SohSessionCompletedEvent;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.soh.domain.entity.CycleCount;
import com.storelense.soh.domain.entity.SohResult;
import com.storelense.soh.domain.entity.SohSession;
import com.storelense.soh.domain.entity.SohSessionItem;
import com.storelense.soh.domain.repository.CycleCountRepository;
import com.storelense.soh.domain.repository.SohSessionItemRepository;
import com.storelense.soh.domain.repository.SohSessionRepository;
import com.storelense.soh.domain.repository.StoreLocationRepository;
import com.storelense.soh.dto.*;
import com.storelense.soh.mapper.SohMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SohSessionService {

    private final SohSessionRepository     sessionRepository;
    private final SohSessionItemRepository itemRepository;
    private final StoreLocationRepository  locationRepository;
    private final CycleCountRepository     cycleCountRepository;
    private final SohMapper                sohMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcClient               jdbcClient;
    private final CycleCountService        cycleCountService;

    // ── List / Get ─────────────────────────────────────────────────────────────

    @Transactional
    public PageResponse<SohSessionResponse> listSessions(UUID storeId, String status, Pageable pageable) {
        var page = (status != null && !status.isBlank())
                ? sessionRepository.findByStoreIdAndStatusInOrderByStartedAtDesc(
                        storeId, List.of(status.split(",")), pageable)
                : sessionRepository.findByStoreIdOrderByStartedAtDesc(storeId, pageable);
        page.forEach(this::refreshReadStatsIfCompleted);
        return PageResponse.from(page.map(sohMapper::toResponse));
    }

    @Transactional
    public SohSessionResponse getSession(UUID sessionId, boolean includeEpcs) {
        SohSession session = findOrThrow(sessionId);
        refreshReadStatsIfCompleted(session);
        SohSessionResponse base = sohMapper.toResponse(session);
        List<String> expectedEpcs = includeEpcs ? fetchExpectedEpcs(session) : null;
        SohResultResponse result = session.getResult() != null
                ? sohMapper.toResultResponse(session.getResult())
                : null;
        return new SohSessionResponse(
                base.id(), base.storeId(), base.zoneId(),
                base.sessionType(), base.status(),
                base.startedBy(), base.startedAt(),
                base.completedAt(), base.pausedAt(), base.resumedAt(),
                base.uploadedAt(), base.reconciledAt(), base.closedAt(),
                base.totalEpcReads(), base.uniqueEpcCount(),
                base.notes(), base.source(), base.zoneRegion(),
                base.cycleCountId(), base.locationCode(), base.sectionCode(),
                expectedEpcs, result
        );
    }

    // ── Start ──────────────────────────────────────────────────────────────────

    @Transactional
    public SohSessionResponse startSession(StartSessionRequest req, UUID userId, UUID effectiveStoreId) {
        // Validate location/section when linked to a cycle count
        if (req.cycleCountId() != null) {
            validateLocation(effectiveStoreId, req.locationCode(), req.sectionCode());
        }

        // Prevent concurrent active sessions for the same store + location combination.
        // A store may have one Floor session and one Backroom session active simultaneously
        // (Scenario 6), but not two Floor sessions with the same section.
        sessionRepository.findActiveSessions(effectiveStoreId).stream()
                .filter(s -> locationConflicts(s, req.locationCode(), req.sectionCode()))
                .findFirst()
                .ifPresent(s -> {
                    throw new BusinessException("SESSION_ACTIVE",
                            "An active session already exists for this store / location",
                            HttpStatus.CONFLICT);
                });

        // Manual/handheld-started audits must be backed by an ERP import — scanning without
        // ERP data isn't supported. erp_triggered sessions are exempt: they're only ever
        // created internally (createFromErpImport) FROM a completed batch, so the check
        // would be redundant there.
        if (!"erp_triggered".equals(req.source()) && !hasCompletedErpImport(effectiveStoreId, req.zoneRegion())) {
            throw new BusinessException("ERP_IMPORT_REQUIRED",
                    "No completed ERP import found for this store/zone. "
                            + "Import ERP data before starting a scan.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Auto-group independent Sales Floor / Back Room scans under one CycleCount so their
        // combined results can be reconciled against the ERP's store-wide expected qty (the
        // ERP feed has no per-zone breakdown — EAN/qty/storeId only — so a Sales Floor session
        // and a Back Room session must be compared together, not each against a per-zone total
        // that doesn't exist). Explicit cycleCountId (e.g. multi-zone Cycle Count flow) wins.
        UUID effectiveCycleCountId = req.cycleCountId();
        if (effectiveCycleCountId == null
                && ("SALES_FLOOR".equals(req.locationCode()) || "BACKROOM".equals(req.locationCode()))) {
            effectiveCycleCountId = cycleCountService.findOrCreateForZoneScan(effectiveStoreId, userId);
        }

        SohSession session = SohSession.builder()
                .storeId(effectiveStoreId)
                .cycleCountId(effectiveCycleCountId)
                .zoneId(req.zoneId())
                .sessionType(req.sessionType() != null ? req.sessionType() : "manual")
                .status("in_progress")
                .source(req.source() != null ? req.source() : "manual")
                .zoneRegion(req.zoneRegion())
                .locationCode(req.locationCode())
                .sectionCode(req.sectionCode())
                .startedBy(userId)
                .notes(req.notes())
                .build();

        SohSession saved = sessionRepository.save(session);

        // Advance parent cycle count from DRAFT → RUNNING on first session start
        if (effectiveCycleCountId != null) {
            cycleCountService.onSessionStarted(effectiveCycleCountId);
        }

        return sohMapper.toResponse(saved);
    }

    @Transactional
    public SohSessionResponse createFromErpImport(ErpImportCompletedEvent event) {
        // Auto-create a CycleCount so the ERP-triggered session is visible on the CC page
        CycleCount cc = cycleCountRepository.save(
                CycleCount.builder()
                        .storeId(event.storeId())
                        .countDate(LocalDate.now())
                        .status("RUNNING")
                        .createdBy(event.batchId())
                        .notes("Auto-created from ERP import batch " + event.batchId())
                        .build()
        );

        SohSession session = SohSession.builder()
                .storeId(event.storeId())
                .sessionType("erp_triggered")
                .status("in_progress")
                .source("erp_triggered")
                .cycleCountId(cc.getId())
                .zoneRegion(event.zoneRegion())
                .startedBy(event.batchId())
                .notes("Auto-created from ERP import batch " + event.batchId())
                .build();
        return sohMapper.toResponse(sessionRepository.save(session));
    }

    // ── EPC count increment (called by Kafka consumer in inventory-service) ────

    @Transactional
    public void incrementEpcCount(UUID sessionId, UUID productId, UUID zoneId,
                                   String locationCode, String sectionCode) {
        SohSession session = findOrThrow(sessionId);
        if (!session.isEditable()) {
            throw new BusinessException("SESSION_NOT_EDITABLE", "Session is not in an editable state");
        }

        // Resolve effective location: prefer event-level value, fall back to session-level
        String effLocation = locationCode != null ? locationCode : session.getLocationCode();
        String effSection  = sectionCode  != null ? sectionCode  : session.getSectionCode();

        SohSessionItem item = itemRepository
                .findBySession_IdAndProductIdAndZoneIdAndLocationCode(
                        sessionId, productId, zoneId, effLocation)
                .orElseGet(() -> {
                    SohSessionItem newItem = SohSessionItem.builder()
                            .session(session)
                            .productId(productId)
                            .zoneId(zoneId)
                            .locationCode(effLocation)
                            .sectionCode(effSection)
                            .countedQuantity(0)
                            .build();
                    return itemRepository.save(newItem);
                });

        itemRepository.incrementCount(item.getId(), 1);
        session.setTotalEpcReads(session.getTotalEpcReads() + 1);
        sessionRepository.save(session);
    }

    // ── Lifecycle transitions ──────────────────────────────────────────────────

    @Transactional
    public SohResultResponse completeSession(UUID sessionId) {
        SohSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("SohSession", sessionId));
        if ("completed".equals(session.getStatus())) {
            // Idempotent: a duplicate/retried "Finish Zone" call (double-tap, network retry)
            // must not recompute and re-insert SohResult — session_id is unique on soh_results.
            return sohMapper.toResultResponse(session.getResult());
        }
        if (!"in_progress".equals(session.getStatus()) && !"paused".equals(session.getStatus())) {
            throw new BusinessException("SESSION_NOT_ACTIVE",
                    "Session must be in_progress or paused to complete");
        }

        SohResult result = calculateResult(session);
        session.setResult(result);
        session.setStatus("completed");
        session.setCompletedAt(OffsetDateTime.now());
        applyRfidReadStats(session);
        sessionRepository.save(session);

        kafkaTemplate.send(KafkaTopics.SOH_SESSION_COMPLETED, sessionId.toString(),
                new SohSessionCompletedEvent(sessionId, session.getStoreId(), result.getAccuracyPct()));

        // Check whether all sessions under the parent count are now complete
        if (session.getCycleCountId() != null) {
            cycleCountService.onSessionCompleted(session.getCycleCountId());

            // Cross-session EPC overlap check: flag EPCs scanned in more than one location
            checkCrossSessionEpcOverlap(session);
        }

        return sohMapper.toResultResponse(result);
    }

    @Transactional
    public void pauseSession(UUID sessionId) {
        SohSession session = findOrThrow(sessionId);
        if (!"in_progress".equals(session.getStatus())) {
            throw new BusinessException("SESSION_NOT_IN_PROGRESS",
                    "Only in_progress sessions can be paused");
        }
        session.setStatus("paused");
        session.setPausedAt(OffsetDateTime.now());
        sessionRepository.save(session);
        log.info("Session {} paused", sessionId);
    }

    @Transactional
    public void resumeSession(UUID sessionId) {
        SohSession session = findOrThrow(sessionId);
        if (!"paused".equals(session.getStatus())) {
            throw new BusinessException("SESSION_NOT_PAUSED",
                    "Only paused sessions can be resumed");
        }
        session.setStatus("in_progress");
        session.setResumedAt(OffsetDateTime.now());
        sessionRepository.save(session);
        log.info("Session {} resumed", sessionId);
    }

    @Transactional
    public void uploadSession(UUID sessionId) {
        SohSession session = findOrThrow(sessionId);
        if (!"completed".equals(session.getStatus())) {
            throw new BusinessException("SESSION_NOT_COMPLETED",
                    "Only completed sessions can be marked as uploaded");
        }
        session.setStatus("uploaded");
        session.setUploadedAt(OffsetDateTime.now());
        sessionRepository.save(session);
        log.info("Session {} marked uploaded", sessionId);
    }

    @Transactional
    public void cancelSession(UUID sessionId, String reason) {
        SohSession session = findOrThrow(sessionId);
        if (!session.isEditable()) {
            throw new BusinessException("SESSION_NOT_EDITABLE", "Session cannot be cancelled");
        }
        session.setStatus("cancelled");
        session.setCancelledAt(OffsetDateTime.now());
        session.setCancellationReason(reason);
        sessionRepository.save(session);
    }

    // ── EPC queries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<String> getSessionEpcs(UUID sessionId) {
        findOrThrow(sessionId);
        List<String> fromRfid = jdbcClient.sql("""
                SELECT DISTINCT epc FROM rfid.rfid_reads
                WHERE rfid_session_id = :sessionId::uuid
                """)
                .param("sessionId", sessionId.toString())
                .query(String.class).list();

        if (!fromRfid.isEmpty()) return fromRfid;

        return jdbcClient.sql("""
                SELECT DISTINCT er.epc
                FROM inventory.epc_registry er
                JOIN soh.soh_sessions ss ON ss.id = :sessionId::uuid
                WHERE er.store_id = ss.store_id
                  AND er.last_seen_at >= ss.started_at
                  AND (ss.completed_at IS NULL OR er.last_seen_at <= ss.completed_at)
                  AND (ss.zone_id IS NULL OR er.zone_id = ss.zone_id)
                """)
                .param("sessionId", sessionId.toString())
                .query(String.class).list();
    }

    // GET /sessions/{id}/expected-epcs — this is what the Android app's live scan screen
    // actually polls at session start (a separate REST path from getSession(includeEpcs=true)).
    // Was previously its own unscoped `epc_registry WHERE store_id = ...` query, completely
    // bypassing fetchExpectedEpcs()'s ERP-batch/zone scoping — the live "Expected" ring stayed
    // store-wide (e.g. 106) even after that scoping was fixed everywhere else it's used.
    @Transactional(readOnly = true)
    public List<String> getExpectedEpcs(UUID sessionId) {
        SohSession session = findOrThrow(sessionId);
        return fetchExpectedEpcs(session);
    }

    /**
     * Self-correcting read-stats refresh for completed sessions: applyRfidReadStats() is
     * only ever invoked once, at completeSession() time — if that ran before the async
     * RFID ingest→resolve pipeline had finished writing to rfid.rfid_reads (a real race we
     * hit in production: reads landing moments after the completion response was sent), the
     * wrong zero values got permanently frozen with no way to refresh, since completeSession
     * is idempotent and never recomputes for an already-completed session. Re-running this
     * live on every list/get call makes it self-correcting instead of a one-shot snapshot —
     * only writes back when the recomputed value actually differs, to avoid needless churn.
     */
    private void refreshReadStatsIfCompleted(SohSession session) {
        if (!"completed".equals(session.getStatus())) return;
        int before = session.getTotalEpcReads();
        int beforeUnique = session.getUniqueEpcCount();
        applyRfidReadStats(session);
        if (session.getTotalEpcReads() != before || session.getUniqueEpcCount() != beforeUnique) {
            sessionRepository.save(session);
        }
    }

    /**
     * Populates totalEpcReads/uniqueEpcCount from the actual rfid.rfid_reads log so the
     * session list (Dashboard/SOH Sessions) shows real numbers instead of the counters
     * that only incrementEpcCount() (never invoked by any producer) would update.
     */
    private void applyRfidReadStats(SohSession session) {
        try {
            record ReadStats(int totalReads, int uniqueEpcs) {}
            ReadStats stats = jdbcClient.sql("""
                    SELECT COUNT(*) AS total_reads, COUNT(DISTINCT epc) AS unique_epcs
                    FROM rfid.rfid_reads
                    WHERE rfid_session_id = :sessionId::uuid
                    """)
                    .param("sessionId", session.getId().toString())
                    .query((rs, n) -> new ReadStats(rs.getInt("total_reads"), rs.getInt("unique_epcs")))
                    .single();
            session.setTotalEpcReads(stats.totalReads());
            session.setUniqueEpcCount(stats.uniqueEpcs());
        } catch (Exception ex) {
            log.warn("Could not compute RFID read stats for session {}: {}",
                    session.getId(), ex.getMessage());
        }
    }

    // ── Result calculation ─────────────────────────────────────────────────────

    private SohResult calculateResult(SohSession session) {
        var items = session.getItems();
        int totalCounted = items.stream().mapToInt(SohSessionItem::getCountedQuantity).sum();

        if (totalCounted == 0) {
            try {
                Integer rawCount = jdbcClient.sql("""
                        SELECT COUNT(DISTINCT epc)
                        FROM rfid.rfid_reads
                        WHERE rfid_session_id = :sessionId::uuid
                        """)
                        .param("sessionId", session.getId().toString())
                        .query(Integer.class).optional().orElse(0);
                totalCounted = rawCount != null ? rawCount : 0;
            } catch (Exception ex) {
                log.warn("Could not compute fallback EPC count for session {}: {}",
                        session.getId(), ex.getMessage());
            }
        }

        int totalExpected = fetchExpectedCount(session);
        int varianceCount = (int) items.stream()
                .filter(i -> i.getCountedQuantity() != i.getExpectedQuantity()).count();
        int overcount  = (int) items.stream()
                .filter(i -> i.getCountedQuantity() > i.getExpectedQuantity()).count();
        int undercount = (int) items.stream()
                .filter(i -> i.getCountedQuantity() < i.getExpectedQuantity()).count();

        // "Nothing expected" is only trivially 100% accurate if nothing was scanned either —
        // scanning real tags against a zero expected count (e.g. this session's ERP batch
        // resolution came up empty while a nonzero count exists elsewhere for the store) means
        // every read is unaccounted for, i.e. 0% match, not 100%.
        BigDecimal accuracy;
        if (totalExpected == 0) {
            accuracy = totalCounted == 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        } else {
            accuracy = BigDecimal.valueOf(100.0 * totalCounted / totalExpected)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        // ── Per-location breakdown ─────────────────────────────────────────────
        UUID sid = session.getId();

        int floorCounted  = itemRepository.sumCountedByLocation(sid, "SALES_FLOOR");
        int floorExpected = itemRepository.sumExpectedByLocation(sid, "SALES_FLOOR");
        int backCounted   = itemRepository.sumCountedByLocation(sid, "BACKROOM");
        int backExpected  = itemRepository.sumExpectedByLocation(sid, "BACKROOM");

        return SohResult.builder()
                .session(session)
                .storeId(session.getStoreId())
                .totalProductsCounted(items.size())
                .totalUnitsCounted(totalCounted)
                .totalUnitsExpected(totalExpected)
                .accuracyPct(accuracy)
                .varianceCount(varianceCount)
                .overcountItems(overcount)
                .undercountItems(undercount)
                .floorUnitsCounted(floorCounted)
                .floorUnitsExpected(floorExpected)
                .floorVariance(floorCounted - floorExpected)
                .backroomUnitsCounted(backCounted)
                .backroomUnitsExpected(backExpected)
                .backroomVariance(backCounted - backExpected)
                .totalStoreVariance(totalCounted - totalExpected)
                .build();
    }

    /**
     * Gate for starting a manual scan: is there a completed ERP import for this store at all?
     *
     * Only Sales Floor and Back Room are recognized — the zone picker only offers those two
     * (Full Store / Fitting Rooms were removed) — so a missing/unrecognized zone still fails
     * the gate. But the ERP feed itself carries no zone breakdown (EAN, qty, storeId only;
     * erp_soh_snapshot.zone_region is NULL for every row in practice — the ZONE_REGION column
     * seen in early test CSVs was test data, not the real feed shape), so this only checks for
     * a completed batch at the store level, not a zone match.
     */
    private boolean hasCompletedErpImport(UUID storeId, String zoneRegion) {
        String normalized = zoneRegion == null
                ? ""
                : zoneRegion.toUpperCase().replace(" ", "").replace("_", "");
        boolean isSalesFloor = normalized.equals("SALESFLOOR");
        boolean isBackRoom   = normalized.equals("BACKROOM");
        if (!isSalesFloor && !isBackRoom) {
            return false;
        }
        Boolean exists = jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM erp.erp_import_batch b
                    WHERE b.store_id = :storeId AND b.status = 'COMPLETED'
                )
                """)
                .param("storeId", storeId)
                .query(Boolean.class).optional().orElse(false);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Resolves the ERP import batch this session's audit should be scoped to. The Android
     * "start audit" flow (CreateSohSessionRequest, storeId only) never sets source=erp_triggered
     * or links a batch — only the internal Kafka consumer (createFromErpImport) does, and no
     * REST path ever creates a session that way. So for the manual sessions actually used in
     * practice, fall back to the latest COMPLETED ERP import for this store instead of
     * requiring an explicit erp_triggered link — otherwise "Expected" always falls through to
     * the unscoped store-wide EPC count.
     *
     * Not zone-scoped: the ERP feed is EAN/qty/storeId only, with no per-zone breakdown, so
     * batch selection is store-wide regardless of which zone the session was started for.
     */
    private java.util.Optional<UUID> resolveErpBatchId(SohSession session) {
        if ("erp_triggered".equals(session.getSource()) && session.getStartedBy() != null) {
            return java.util.Optional.of(session.getStartedBy());
        }
        try {
            var q = jdbcClient.sql("""
                    SELECT b.id FROM erp.erp_import_batch b
                    WHERE b.store_id = :storeId AND b.status = 'COMPLETED'
                    ORDER BY b.imported_at DESC
                    LIMIT 1
                    """).param("storeId", session.getStoreId());
            return q.query(UUID.class).optional();
        } catch (Exception ex) {
            log.warn("Could not resolve ERP batch for session {}: {}", session.getId(), ex.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * Numeric "expected" target for the audit — the ERP's declared expected_qty summed across
     * every imported row for the resolved batch. NOT the count of EPCs already resolved/linked
     * in erp_soh_snapshot_epcs (fetchExpectedEpcs), which silently drops any row whose EAN
     * hasn't been matched to an EPC yet and would otherwise make "Expected" shrink below the
     * true ERP total whenever a product is unresolved.
     */
    private int fetchExpectedCount(SohSession session) {
        var batchId = resolveErpBatchId(session);
        if (batchId.isPresent()) {
            try {
                Integer sum = jdbcClient.sql("""
                        SELECT COALESCE(SUM(expected_qty), 0)
                        FROM erp.erp_soh_snapshot
                        WHERE batch_id = :batchId::uuid
                        """)
                        .param("batchId", batchId.get().toString())
                        .query(Integer.class).single();
                if (sum != null) return sum;
            } catch (Exception ex) {
                log.warn("Could not fetch ERP expected_qty sum for session {}: {}",
                        session.getId(), ex.getMessage());
            }
        }
        return fetchExpectedEpcs(session).size();
    }

    // ── Cross-session EPC overlap check ───────────────────────────────────────

    private void checkCrossSessionEpcOverlap(SohSession justCompleted) {
        UUID cycleCountId = justCompleted.getCycleCountId();
        if (cycleCountId == null) return;

        List<SohSession> siblings = sessionRepository
                .findByCycleCountIdOrderByStartedAtAsc(cycleCountId)
                .stream()
                .filter(s -> !s.getId().equals(justCompleted.getId())
                        && ("completed".equals(s.getStatus()) || "uploaded".equals(s.getStatus())))
                .toList();

        if (siblings.isEmpty()) return;

        try {
            // Collect EPCs from the just-completed session
            Set<String> myEpcs = new java.util.HashSet<>(jdbcClient.sql("""
                    SELECT DISTINCT epc FROM rfid.rfid_reads
                    WHERE rfid_session_id IN (
                        SELECT id FROM rfid.rfid_sessions
                        WHERE soh_session_id = :sessionId::uuid
                    )
                    """)
                    .param("sessionId", justCompleted.getId().toString())
                    .query(String.class).list());

            for (SohSession sibling : siblings) {
                Set<String> siblingEpcs = new java.util.HashSet<>(jdbcClient.sql("""
                        SELECT DISTINCT epc FROM rfid.rfid_reads
                        WHERE rfid_session_id IN (
                            SELECT id FROM rfid.rfid_sessions
                            WHERE soh_session_id = :sessionId::uuid
                        )
                        """)
                        .param("sessionId", sibling.getId().toString())
                        .query(String.class).list());

                Set<String> overlap = myEpcs.stream()
                        .filter(siblingEpcs::contains)
                        .collect(Collectors.toSet());

                if (!overlap.isEmpty()) {
                    log.warn("CycleCount {} — {} EPC(s) scanned in both session {} ({}) and session {} ({}): {}",
                            cycleCountId, overlap.size(),
                            justCompleted.getId(), justCompleted.getLocationCode(),
                            sibling.getId(), sibling.getLocationCode(),
                            overlap.size() > 5 ? overlap.stream().limit(5).collect(Collectors.joining(",")) + "…"
                                               : String.join(",", overlap));

                    // Persist overlapping EPCs as investigation flags in soh_variance
                    overlap.forEach(epc -> {
                        try {
                            jdbcClient.sql("""
                                    INSERT INTO soh.soh_variance
                                        (session_id, result_id, store_id, product_id,
                                         zone_id, counted_qty, expected_qty, variance_qty,
                                         variance_type, requires_investigation, investigation_notes)
                                    SELECT
                                        :sessionId::uuid,
                                        r.id,
                                        :storeId::uuid,
                                        COALESCE(
                                            (SELECT et.product_id FROM products.epc_tags et
                                             WHERE et.epc = :epc AND et.is_active = true LIMIT 1),
                                            '00000000-0000-0000-0000-000000000000'::uuid
                                        ),
                                        NULL, 1, 1, 0,
                                        'match',
                                        true,
                                        'EPC scanned in multiple locations: ' || :otherSession || ' (' || :otherLocation || ')'
                                    FROM soh.soh_results r
                                    WHERE r.session_id = :sessionId::uuid
                                    """)
                                    .param("sessionId", justCompleted.getId().toString())
                                    .param("storeId", justCompleted.getStoreId().toString())
                                    .param("epc", epc)
                                    .param("otherSession", sibling.getId().toString())
                                    .param("otherLocation",
                                           sibling.getLocationCode() != null ? sibling.getLocationCode() : "unknown")
                                    .update();
                        } catch (Exception ex) {
                            log.debug("Could not persist overlap flag for EPC {}: {}", epc, ex.getMessage());
                        }
                    });
                }
            }
        } catch (Exception ex) {
            log.warn("Cross-session EPC overlap check failed for cycle count {}: {}",
                    cycleCountId, ex.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void validateLocation(UUID storeId, String locationCode, String sectionCode) {
        if (locationCode == null) {
            throw new BusinessException("LOCATION_REQUIRED",
                    "locationCode is required when linking a session to a cycle count",
                    HttpStatus.BAD_REQUEST);
        }
        // If the store has no configured locations yet, accept the well-known defaults
        // so stores can operate before an admin sets up store_locations.
        boolean hasAnyConfigured = locationRepository
                .findByStoreIdAndIsActiveTrueOrderBySortOrderAsc(storeId)
                .size() > 0;
        if (!hasAnyConfigured) return;

        boolean valid = locationRepository.existsByStoreIdAndLocationCodeAndSectionCodeAndIsActiveTrue(
                storeId, locationCode, sectionCode);
        if (!valid) {
            throw new BusinessException("INVALID_LOCATION",
                    "No active store location found for locationCode=" + locationCode
                            + " sectionCode=" + sectionCode,
                    HttpStatus.BAD_REQUEST);
        }
    }

    private boolean locationConflicts(SohSession existing, String newLocation, String newSection) {
        if (newLocation == null) return false;
        // Same location AND same section (or both null sections) = conflict
        return newLocation.equals(existing.getLocationCode())
                && java.util.Objects.equals(newSection, existing.getSectionCode());
    }

    private List<String> fetchExpectedEpcs(SohSession session) {
        var batchId = resolveErpBatchId(session);
        if (batchId.isPresent()) {
            try {
                // Resolve by product match against this batch's imported EANs — NOT just
                // erp_soh_snapshot_epcs, which only holds EPCs already linked/resolved and is
                // empty right after import, silently falling through to the unscoped
                // store-wide query below (the same bug already fixed in fetchExpectedCount).
                // Not zone-scoped: the ERP feed has no per-zone breakdown (EAN/qty/storeId
                // only), so every product expected at the store is expected regardless of
                // which zone this particular session is scanning — the real zone split is
                // discovered by scanning, not declared by ERP.
                return jdbcClient.sql("""
                        SELECT DISTINCT er.epc
                        FROM inventory.epc_registry er
                        JOIN products.epc_tags et ON et.epc = er.epc AND et.is_active = true
                        JOIN products.barcodes b  ON b.product_id = et.product_id
                        JOIN erp.erp_soh_snapshot s ON UPPER(b.barcode_value) = UPPER(s.ean)
                        WHERE s.batch_id = :batchId::uuid
                          AND er.store_id = :storeId
                          AND er.status NOT IN ('sold', 'damaged', 'transferred')
                        LIMIT 10000
                        """)
                        .param("batchId", batchId.get().toString())
                        .param("storeId", session.getStoreId())
                        .query(String.class).list();
            } catch (Exception ex) {
                log.warn("Could not fetch ERP expected EPCs for session {}: {}",
                        session.getId(), ex.getMessage());
                return List.of();
            }
        }
        try {
            boolean hasZone = session.getZoneRegion() != null && !session.getZoneRegion().isBlank();
            String sql = hasZone
                    ? """
                      SELECT er.epc FROM inventory.epc_registry er
                      JOIN inventory.inventory_state ist
                        ON ist.product_id = (
                             SELECT et.product_id FROM products.epc_tags et
                             WHERE et.epc = er.epc AND et.is_active = true LIMIT 1
                           )
                       AND ist.store_id = er.store_id
                       AND UPPER(REPLACE(REPLACE(ist.zone_region, ' ', ''), '_', ''))
                         = UPPER(REPLACE(REPLACE(:zoneRegion, ' ', ''), '_', ''))
                      WHERE er.store_id = :storeId
                        AND er.status NOT IN ('sold', 'damaged', 'transferred')
                      LIMIT 10000
                      """
                    : """
                      SELECT epc FROM inventory.epc_registry
                      WHERE store_id = :storeId
                        AND status NOT IN ('sold', 'damaged', 'transferred')
                      LIMIT 10000
                      """;
            var q = jdbcClient.sql(sql).param("storeId", session.getStoreId());
            if (hasZone) q = q.param("zoneRegion", session.getZoneRegion());
            return q.query(String.class).list();
        } catch (Exception ex) {
            log.warn("Could not fetch system expected EPCs for session {}: {}",
                    session.getId(), ex.getMessage());
            return List.of();
        }
    }

    private SohSession findOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SohSession", id));
    }
}
