package com.storelense.notification.consumer;

import com.storelense.common.event.RefillTaskCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefillNotificationConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "refill.task.created", groupId = "notification-service")
    public void onRefillTaskCreated(RefillTaskCreatedEvent event) {
        String dest = "/topic/stores/" + event.storeId() + "/refill";
        messagingTemplate.convertAndSend(dest, Map.of(
                "type",    "REFILL_TASK_CREATED",
                "taskId",  event.taskId(),
                "storeId", event.storeId(),
                "source",  event.source()
        ));
    }
}
