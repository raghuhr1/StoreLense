package com.storelense.refill.consumer;

import com.storelense.common.event.SohSessionCompletedEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.refill.service.SohAutoReplenishmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SohSessionConsumer {

    private final SohAutoReplenishmentService autoReplenishmentService;

    @KafkaListener(topics = KafkaTopics.SOH_SESSION_COMPLETED, groupId = "refill-service")
    public void onSohSessionCompleted(SohSessionCompletedEvent event) {
        log.info("SOH session completed: sessionId={} storeId={} accuracyPct={} — checking Sales Floor par levels",
                event.sessionId(), event.storeId(), event.accuracyPct());
        autoReplenishmentService.onSohSessionCompleted(event);
    }
}
