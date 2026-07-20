package com.storelense.erp.consumer;

import com.storelense.common.event.SohSessionCompletedEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.erp.service.ReconciliationEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SohSessionCompletedConsumer {

    private final ReconciliationEngine reconciliationEngine;

    // default.type is a fallback used only when a message has no __TypeId__ header (older
    // messages produced before headers were consistently set) — with type headers present
    // (the normal case), those still take priority. Without this, any header-less message
    // permanently dead-letters with "No type information in headers and no default type
    // provided", silently breaking auto-reconciliation for every session after it in the
    // same partition until manually cleared.
    @KafkaListener(topics = KafkaTopics.SOH_SESSION_COMPLETED, groupId = "erp-reconciliation",
            properties = "spring.json.value.default.type=com.storelense.common.event.SohSessionCompletedEvent")
    public void onSohSessionCompleted(SohSessionCompletedEvent event) {
        log.info("SOH session completed — auto-running reconciliation: sessionId={} storeId={}",
                event.sessionId(), event.storeId());
        try {
            var recon = reconciliationEngine.reconcile(event.sessionId());
            if (recon == null) {
                // Part of a cycle count — deliberately skipped here; it's reconciled
                // together with its sibling zones once the cycle count itself completes.
                log.info("Skipped individual reconciliation for session {} — part of a cycle count",
                        event.sessionId());
                return;
            }
            log.info("Auto-reconciliation complete: sessionId={} matched={} missing={} extra={} accuracy={}%",
                    event.sessionId(), recon.getMatchedCount(), recon.getMissingCount(),
                    recon.getExtraCount(), recon.getAccuracyPct());
        } catch (Exception e) {
            // Non-fatal: no ERP batch for this store, or session has no ERP import.
            // The user can still run reconciliation manually via POST /api/reconciliation/sessions/{id}/run
            log.warn("Auto-reconciliation skipped for session {}: {}", event.sessionId(), e.getMessage());
        }
    }
}
