package com.storelense.notification.consumer;

import com.storelense.common.event.SohSessionCompletedEvent;
import com.storelense.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SohNotificationConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = KafkaTopics.SOH_SESSION_COMPLETED, groupId = "notification-service")
    public void onSohSessionCompleted(SohSessionCompletedEvent event) {
        String dest = "/topic/stores/" + event.storeId() + "/soh";
        messagingTemplate.convertAndSend(dest, Map.of(
                "type",       "SOH_SESSION_COMPLETED",
                "sessionId",  event.sessionId(),
                "storeId",    event.storeId(),
                "accuracyPct", event.accuracyPct()
        ));
        log.debug("Pushed SOH completion to WebSocket topic {}", dest);
    }
}
