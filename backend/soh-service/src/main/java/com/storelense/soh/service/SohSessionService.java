package com.storelense.soh.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.event.ErpImportCompletedEvent;
import com.storelense.common.event.SohSessionCompletedEvent;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.soh.domain.entity.SohResult;
import com.storelense.soh.domain.entity.SohSession;
import com.storelense.soh.domain.entity.SohSessionItem;
import com.storelense.soh.domain.repository.SohSessionItemRepository;
import com.storelense.soh.domain.repository.SohSessionRepository;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SohSessionService {

    private final SohSessionRepository     sessionRepository;
    private final SohSessionItemRepository itemRepository;
    private final SohMapper                sohMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcClient               jdbcClient;

    @Transactional(readOnly = true)
    public PageResponse<SohSessionResponse> listSessions(UUID storeId, String status, Pageable pageable) {
        var page = (status != null && !status.isBlank())
                ? sessionRepository.findByStoreIdAndStatusInOrderByStartedAtDesc(
                        storeId, List.of(status.split(",")), pageable)
                : sessionRepository.findByStoreIdOrderByStartedAtDesc(storeId, pageable);
        return PageResponse.from(page.map(sohMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public SohSessionResponse getSession(UUID sessionId) {
        SohSession session = findOrThrow(sessionId);
        SohSessionResponse base = sohMapper.toResponse(session);
        List<String> expectedEpcs = fetchExpectedEpcs(session);
        return new SohSessionResponse(
                base.id(), base.storeId(), base.zoneId(),
                base.sessionType(), base.status(),
                base.startedBy(), base.startedAt(),
                base.completedAt(),
                base.totalEpcReads(), base.uniqueEpcCount(),
                base.notes(), base.source(), base.zoneRegion(),
                expectedEpcs
        );
    }

    private List<String> fetchExpectedEpcs(SohSession session) {
        // ERP-triggered: expected EPCs come from the ERP import snapshot
        if ("erp_triggered".equals(session.getSource()) && session.getStartedBy() != null) {
            try {
                List<String> erpEpcs = jdbcClient.sql("""
                        SELECT e.epc
                        FROM erp.erp_soh_snapshot_epcs e
                        JOIN erp.erp_soh_snapshot s ON s.id = e.snapshot_id
                        WHERE s.batch_id = :batchId::uuid
                        LIMIT 10000
                        """)
                        .param("batchId", session.getStartedBy().toString())
                        .query(String.class)
                        .list();
                if (!erpEpcs.isEmpty()) return erpEpcs;
            } catch (Exception ex) {
                log.warn("Could not fetch ERP expected EPCs for session {}: {}", session.getId(), ex.getMessage());
            }
        }
        // System-driven fallback: non-sold EPCs already tracked in epc_registry.
        // Covers manual sessions and stores that haven't uploaded an ERP CSV.
        // When zoneRegion is set (zone-wise scan), filter expected EPCs to that zone.
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
                       AND ist.zone_region = :zoneRegion
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
            log.warn("Could not fetch system expected EPCs for session {}: {}", session.getId(), ex.getMessage());
            return List.of();
        }
    }

    @Transactional
    public SohSessionResponse startSession(StartSessionRequest req, UUID userId, UUID effectiveStoreId) {
        // Prevent concurrent sessions for same store+zone
        sessionRepository.findActiveSession(effectiveStoreId).ifPresent(s -> {
            throw new BusinessException("SESSION_ACTIVE",
                    "An active session already exists for this store", HttpStatus.CONFLICT);
        });

        SohSession session = SohSession.builder()
                .storeId(effectiveStoreId)
                .zoneId(req.zoneId())
                .sessionType(req.sessionType() != null ? req.sessionType() : "manual")
                .status("in_progress")
                .source(req.source() != null ? req.source() : "manual")
                .zoneRegion(req.zoneRegion())
                .startedBy(userId)
                .notes(req.notes())
                .build();

        return sohMapper.toResponse(sessionRepository.save(session));
    }

    @Transactional
    public SohSessionResponse createFromErpImport(ErpImportCompletedEvent event) {
        SohSession session = SohSession.builder()
                .storeId(event.storeId())
                .sessionType("erp_triggered")
                .status("in_progress")
                .source("erp_triggered")
                .zoneRegion(event.zoneRegion())
                .startedBy(event.batchId())
                .notes("Auto-created from ERP import batch " + event.batchId())
                .build();
        return sohMapper.toResponse(sessionRepository.save(session));
    }

    @Transactional
    public void incrementEpcCount(UUID sessionId, UUID productId, UUID zoneId) {
        SohSession session = findOrThrow(sessionId);
        if (!session.isEditable()) {
            throw new BusinessException("SESSION_NOT_EDITABLE", "Session is not in progress");
        }

        SohSessionItem item = itemRepository
                .findBySession_IdAndProductIdAndZoneId(sessionId, productId, zoneId)
                .orElseGet(() -> {
                    SohSessionItem newItem = SohSessionItem.builder()
                            .session(session)
                            .productId(productId)
                            .zoneId(zoneId)
                            .countedQuantity(0)
                            .build();
                    return itemRepository.save(newItem);
                });

        itemRepository.incrementCount(item.getId(), 1);
        session.setTotalEpcReads(session.getTotalEpcReads() + 1);
        sessionRepository.save(session);
    }

    @Transactional
    public SohResultResponse completeSession(UUID sessionId) {
        SohSession session = findOrThrow(sessionId);
        if (!"in_progress".equals(session.getStatus())) {
            throw new BusinessException("SESSION_NOT_IN_PROGRESS",
                    "Session must be in_progress to complete");
        }

        SohResult result = calculateResult(session);
        session.setResult(result);
        session.setStatus("completed");
        session.setCompletedAt(OffsetDateTime.now());
        sessionRepository.save(session);

        kafkaTemplate.send(KafkaTopics.SOH_SESSION_COMPLETED, sessionId.toString(),
                new SohSessionCompletedEvent(sessionId, session.getStoreId(), result.getAccuracyPct()));

        return sohMapper.toResultResponse(result);
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

    private SohResult calculateResult(SohSession session) {
        var items = session.getItems();
        int totalCounted = items.stream().mapToInt(SohSessionItem::getCountedQuantity).sum();

        // Try ERP-sourced expected quantity first (set by ERP import Kafka consumer)
        int totalExpected = jdbcClient.sql("""
                SELECT COALESCE(SUM(quantity_expected), 0)
                FROM inventory.inventory_state
                WHERE store_id = :storeId
                """)
                .param("storeId", session.getStoreId())
                .query(Integer.class)
                .single();

        // System-driven fallback: count non-sold EPCs in epc_registry.
        // Used when no ERP CSV has been imported but EPCs are tracked (e.g. via guard app sales).
        if (totalExpected == 0) {
            totalExpected = jdbcClient.sql("""
                    SELECT COUNT(*)
                    FROM inventory.epc_registry
                    WHERE store_id = :storeId
                      AND status NOT IN ('sold', 'damaged', 'transferred')
                    """)
                    .param("storeId", session.getStoreId())
                    .query(Integer.class)
                    .single();
            log.debug("System-driven expected for session {}: {} EPCs from epc_registry",
                    session.getId(), totalExpected);
        }
        int varianceCount = (int) items.stream().filter(i -> i.getCountedQuantity() != i.getExpectedQuantity()).count();
        int overcount     = (int) items.stream().filter(i -> i.getCountedQuantity() > i.getExpectedQuantity()).count();
        int undercount    = (int) items.stream().filter(i -> i.getCountedQuantity() < i.getExpectedQuantity()).count();

        BigDecimal accuracy = totalExpected == 0 ? BigDecimal.valueOf(100)
                : BigDecimal.valueOf(100.0 * totalCounted / totalExpected).setScale(2, RoundingMode.HALF_UP);

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
                .build();
    }

    @Transactional(readOnly = true)
    public List<String> getSessionEpcs(UUID sessionId) {
        findOrThrow(sessionId); // 404 if session missing
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
                .query(String.class)
                .list();
    }

    private SohSession findOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SohSession", id));
    }
}
