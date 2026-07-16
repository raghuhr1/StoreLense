package com.storelense.erp.consumer;

import com.storelense.common.event.CycleCountCompletedEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.erp.service.ReconciliationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Fires when every session under a CycleCount (e.g. a Sales Floor scan + a Back Room scan,
 * auto-grouped by SohSessionService.startSession) reaches 'completed'. Runs the COMBINED
 * reconciliation across all of them, because the ERP feed (EAN, qty, storeId — no zone
 * breakdown) can only be matched against the merged scan result, not each zone individually.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CycleCountCompletedConsumer {

    private final ReconciliationEngine reconciliationEngine;

    @KafkaListener(topics = KafkaTopics.CYCLE_COUNT_COMPLETED, groupId = "erp-reconciliation",
            properties = "spring.json.value.default.type=com.storelense.common.event.CycleCountCompletedEvent")
    public void onCycleCountCompleted(CycleCountCompletedEvent event) {
        log.info("Cycle count completed — auto-running combined reconciliation: cycleCountId={} storeId={}",
                event.cycleCountId(), event.storeId());
        try {
            var recon = reconciliationEngine.reconcileByCount(event.cycleCountId());
            log.info("Combined auto-reconciliation complete: cycleCountId={} matched={} missing={} extra={} accuracy={}%",
                    event.cycleCountId(), recon.getMatchedCount(), recon.getMissingCount(),
                    recon.getExtraCount(), recon.getAccuracyPct());
        } catch (Exception e) {
            // Non-fatal: no ERP batch for this store, or no sessions found for the count.
            // Can still be run manually via POST /api/reconciliation/cycle-counts/{id}/run
            log.warn("Combined auto-reconciliation skipped for cycle count {}: {}",
                    event.cycleCountId(), e.getMessage());
        }
    }
}
