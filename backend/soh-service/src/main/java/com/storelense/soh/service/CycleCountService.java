package com.storelense.soh.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.event.CycleCountCompletedEvent;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.soh.domain.entity.CycleCount;
import com.storelense.soh.domain.repository.CycleCountRepository;
import com.storelense.soh.domain.repository.SohSessionRepository;
import com.storelense.soh.dto.CreateCycleCountRequest;
import com.storelense.soh.dto.CycleCountResponse;
import com.storelense.soh.mapper.SohMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CycleCountService {

    private final CycleCountRepository  cycleCountRepository;
    private final SohSessionRepository  sessionRepository;
    private final SohMapper             sohMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Allowed forward transitions ────────────────────────────────────────────
    // DRAFT → RUNNING (first session joined)
    // RUNNING → COMPLETED (all sessions completed)
    // COMPLETED → UPLOADED (ERP push confirmed)
    // UPLOADED → RECONCILED (reconciliation approved)
    // Any → CLOSED (manual close by manager)

    private static final List<String> OPEN_STATUSES = List.of("DRAFT", "RUNNING");

    @Transactional(readOnly = true)
    public PageResponse<CycleCountResponse> list(UUID storeId, Pageable pageable) {
        return PageResponse.from(
                cycleCountRepository
                        .findByStoreIdOrderByCountDateDescCreatedAtDesc(storeId, pageable)
                        .map(sohMapper::toCycleCountResponse)
        );
    }

    @Transactional(readOnly = true)
    public CycleCountResponse get(UUID id) {
        CycleCount cc = findOrThrow(id);
        CycleCountResponse base = sohMapper.toCycleCountResponse(cc);

        List<com.storelense.soh.dto.SohSessionResponse> sessions = sessionRepository
                .findByCycleCountIdOrderByStartedAtAsc(id)
                .stream()
                .map(s -> {
                    var r = sohMapper.toResponse(s);
                    // result sub-object mapped when session is completed
                    var result = s.getResult() != null ? sohMapper.toResultResponse(s.getResult()) : null;
                    return new com.storelense.soh.dto.SohSessionResponse(
                            r.id(), r.storeId(), r.zoneId(),
                            r.sessionType(), r.status(),
                            r.startedBy(), r.startedAt(),
                            r.completedAt(), r.pausedAt(), r.resumedAt(),
                            r.uploadedAt(), r.reconciledAt(), r.closedAt(),
                            r.totalEpcReads(), r.uniqueEpcCount(),
                            r.notes(), r.source(), r.zoneRegion(),
                            r.cycleCountId(), r.locationCode(), r.sectionCode(),
                            null, result
                    );
                })
                .toList();

        return new CycleCountResponse(
                base.id(), base.storeId(), base.countDate(),
                base.status(), base.createdBy(), base.notes(),
                base.createdAt(), base.updatedAt(),
                sessions
        );
    }

    @Transactional
    public CycleCountResponse create(CreateCycleCountRequest req, UUID userId) {
        CycleCount cc = CycleCount.builder()
                .storeId(req.storeId())
                .countDate(req.countDate() != null ? req.countDate() : LocalDate.now())
                .status("DRAFT")
                .createdBy(userId)
                .notes(req.notes())
                .build();
        return sohMapper.toCycleCountResponse(cycleCountRepository.save(cc));
    }

    @Transactional
    public CycleCountResponse transition(UUID id, String targetStatus, UUID actorId) {
        CycleCount cc = findOrThrow(id);
        validateTransition(cc.getStatus(), targetStatus);
        cc.setStatus(targetStatus);
        log.info("CycleCount {} transitioned {} → {} by {}", id, cc.getStatus(), targetStatus, actorId);
        return sohMapper.toCycleCountResponse(cycleCountRepository.save(cc));
    }

    /**
     * Finds today's open (DRAFT/RUNNING) cycle count for this store, or creates one.
     * Used to auto-group independently-started Sales Floor / Back Room scan sessions under
     * a shared CycleCount, since the ERP feed (EAN, qty, storeId — no zone breakdown) can
     * only be reconciled against the COMBINED result of all zone sessions, not each zone
     * individually. See ReconciliationEngine.reconcileByCount, which merges EPCs across every
     * session in a cycle count before comparing against the ERP's store-wide expected qty.
     */
    @Transactional
    public UUID findOrCreateForZoneScan(UUID storeId, UUID createdBy) {
        LocalDate today = LocalDate.now();
        return cycleCountRepository.findActiveByStore(storeId, OPEN_STATUSES).stream()
                .filter(cc -> today.equals(cc.getCountDate()))
                .findFirst()
                .map(CycleCount::getId)
                .orElseGet(() -> cycleCountRepository.save(CycleCount.builder()
                        .storeId(storeId)
                        .countDate(today)
                        .status("DRAFT")
                        .createdBy(createdBy)
                        .notes("Auto-created to group Sales Floor / Back Room scans for combined ERP reconciliation")
                        .build()).getId());
    }

    /** Called by SohSessionService when a session under a count goes in_progress. */
    @Transactional
    public void onSessionStarted(UUID cycleCountId) {
        cycleCountRepository.findById(cycleCountId).ifPresent(cc -> {
            if ("DRAFT".equals(cc.getStatus())) {
                cc.setStatus("RUNNING");
                cycleCountRepository.save(cc);
            }
        });
    }

    /**
     * Called by SohSessionService when every session in the count reaches
     * 'completed'. Advances cycle count status to COMPLETED automatically.
     */
    @Transactional
    public void onSessionCompleted(UUID cycleCountId) {
        cycleCountRepository.findById(cycleCountId).ifPresent(cc -> {
            if (!"RUNNING".equals(cc.getStatus())) return;

            boolean allDone = sessionRepository
                    .findByCycleCountIdOrderByStartedAtAsc(cycleCountId)
                    .stream()
                    .allMatch(s -> "completed".equals(s.getStatus())
                            || "cancelled".equals(s.getStatus())
                            || "closed".equals(s.getStatus()));

            if (allDone) {
                cc.setStatus("COMPLETED");
                cycleCountRepository.save(cc);
                log.info("CycleCount {} auto-advanced to COMPLETED — all sessions done", cycleCountId);
                kafkaTemplate.send(KafkaTopics.CYCLE_COUNT_COMPLETED, cycleCountId.toString(),
                        new CycleCountCompletedEvent(cycleCountId, cc.getStoreId()));
            }
        });
    }

    /** Called by the ERP reconciliation approval flow. */
    @Transactional
    public void markReconciled(UUID cycleCountId, OffsetDateTime reconciledAt) {
        cycleCountRepository.findById(cycleCountId).ifPresent(cc -> {
            cc.setStatus("RECONCILED");
            cycleCountRepository.save(cc);
        });
    }

    private void validateTransition(String current, String target) {
        boolean allowed = switch (target) {
            case "RUNNING"    -> "DRAFT".equals(current);
            case "COMPLETED"  -> "RUNNING".equals(current);
            case "UPLOADED"   -> "COMPLETED".equals(current);
            case "RECONCILED" -> "UPLOADED".equals(current);
            case "CLOSED"     -> true;   // any state can be closed by a manager
            default           -> false;
        };
        if (!allowed) {
            throw new BusinessException("INVALID_TRANSITION",
                    "Cannot transition cycle count from " + current + " to " + target,
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private CycleCount findOrThrow(UUID id) {
        return cycleCountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CycleCount", id));
    }
}
