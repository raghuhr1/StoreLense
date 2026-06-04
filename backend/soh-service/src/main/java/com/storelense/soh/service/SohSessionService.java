package com.storelense.soh.service;

import com.storelense.common.dto.PageResponse;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SohSessionService {

    private final SohSessionRepository     sessionRepository;
    private final SohSessionItemRepository itemRepository;
    private final SohMapper                sohMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional(readOnly = true)
    public PageResponse<SohSessionResponse> listSessions(UUID storeId, String status, Pageable pageable) {
        var page = status != null
                ? sessionRepository.findByStoreIdAndStatusOrderByStartedAtDesc(storeId, status, pageable)
                : sessionRepository.findByStoreIdOrderByStartedAtDesc(storeId, pageable);
        return PageResponse.from(page.map(sohMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public SohSessionResponse getSession(UUID sessionId) {
        return sohMapper.toResponse(findOrThrow(sessionId));
    }

    @Transactional
    public SohSessionResponse startSession(StartSessionRequest req, UUID userId) {
        // Prevent concurrent sessions for same store+zone
        sessionRepository.findActiveSession(req.storeId()).ifPresent(s -> {
            throw new BusinessException("SESSION_ACTIVE",
                    "An active session already exists for this store", HttpStatus.CONFLICT);
        });

        SohSession session = SohSession.builder()
                .storeId(req.storeId())
                .zoneId(req.zoneId())
                .sessionType(req.sessionType() != null ? req.sessionType() : "manual")
                .status("in_progress")
                .startedBy(userId)
                .notes(req.notes())
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
        int totalCounted  = items.stream().mapToInt(SohSessionItem::getCountedQuantity).sum();
        int totalExpected = items.stream().mapToInt(SohSessionItem::getExpectedQuantity).sum();
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

    private SohSession findOrThrow(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SohSession", id));
    }
}
