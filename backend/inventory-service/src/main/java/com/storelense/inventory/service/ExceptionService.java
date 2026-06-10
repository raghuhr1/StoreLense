package com.storelense.inventory.service;

import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.inventory.domain.entity.ExceptionEvent;
import com.storelense.inventory.domain.repository.ExceptionEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExceptionService {

    private static final List<String> ACTIVE = List.of("OPEN", "INVESTIGATING");

    private final ExceptionEventRepository exceptionRepository;

    @Transactional(readOnly = true)
    public Map<String, Long> getSummary(UUID storeId) {
        return Map.of(
                "MISSING_EPC",  exceptionRepository.countByStoreIdAndTypeAndStatusIn(storeId, "MISSING_EPC",  ACTIVE),
                "GHOST_TAG",    exceptionRepository.countByStoreIdAndTypeAndStatusIn(storeId, "GHOST_TAG",    ACTIVE),
                "READ_MISS",    exceptionRepository.countByStoreIdAndTypeAndStatusIn(storeId, "READ_MISS",    ACTIVE),
                "UNDER_REVIEW", exceptionRepository.countByStoreIdAndTypeAndStatusIn(storeId, "UNDER_REVIEW", ACTIVE)
        );
    }

    @Transactional(readOnly = true)
    public Page<ExceptionEvent> listByType(UUID storeId, String type, Pageable pageable) {
        return exceptionRepository.findByStoreIdAndTypeOrderByCreatedAtDesc(storeId, type, pageable);
    }

    @Transactional(readOnly = true)
    public ExceptionEvent getGhostDetail(String epc, UUID storeId) {
        return findActiveOrThrow(epc, storeId, "GHOST_TAG");
    }

    @Transactional(readOnly = true)
    public ExceptionEvent getMissingDetail(String epc, UUID storeId) {
        return findActiveOrThrow(epc, storeId, "MISSING_EPC");
    }

    @Transactional
    public ExceptionEvent ignoreGhost(String epc, UUID storeId) {
        ExceptionEvent event = findActiveOrThrow(epc, storeId, "GHOST_TAG");
        event.setStatus("IGNORED");
        return exceptionRepository.save(event);
    }

    @Transactional
    public ExceptionEvent investigateGhost(String epc, UUID storeId) {
        ExceptionEvent event = findActiveOrThrow(epc, storeId, "GHOST_TAG");
        event.setStatus("INVESTIGATING");
        return exceptionRepository.save(event);
    }

    @Transactional
    public ExceptionEvent markMissing(String epc, UUID storeId) {
        ExceptionEvent event = findActiveOrThrow(epc, storeId, "MISSING_EPC");
        event.setStatus("RESOLVED");
        return exceptionRepository.save(event);
    }

    private ExceptionEvent findActiveOrThrow(String epc, UUID storeId, String type) {
        return exceptionRepository
                .findFirstByEpcAndStoreIdAndTypeAndStatusInOrderByCreatedAtDesc(epc, storeId, type, ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("ExceptionEvent",
                        type + "/" + epc));
    }
}
