package com.storelense.rfid.processing.consumer;

import com.storelense.common.event.RfidReadEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.rfid.processing.dto.DlqRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Consumes records from the RFID reads Dead Letter Topic.
 *
 * Default action: log and discard. Administrators can POST to
 * /api/rfid/dlq/{id}/replay to re-publish to the original topic.
 * This consumer stores failed records in-memory for the admin endpoint
 * (production would persist to rfid_dlq table or an alerting system).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RfidDlqConsumer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // In-memory for demo — replace with a DLQ persistence table in production
    private final java.util.concurrent.ConcurrentLinkedQueue<DlqRecord> dlqStore =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    @KafkaListener(
            topics = KafkaTopics.RFID_READS_RAW + KafkaTopics.DLT_SUFFIX,
            groupId = "rfid-processing-service-dlq"
    )
    public void onDeadLetter(
            ConsumerRecord<String, RfidReadEvent> record,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) Optional<String> exceptionMessage) {

        String error = exceptionMessage.orElse("unknown");
        log.error("RFID read in DLT: topic={} partition={} offset={} key={} epc={} error={}",
                record.topic(), record.partition(), record.offset(), record.key(),
                record.value() != null ? record.value().epc() : "null",
                error);

        dlqStore.add(new DlqRecord(
                record.key(),
                record.value(),
                error,
                OffsetDateTime.now(),
                record.partition(),
                record.offset()
        ));

        // Limit in-memory store to 1000 entries
        while (dlqStore.size() > 1000) dlqStore.poll();
    }

    public java.util.List<DlqRecord> getRecentFailures() {
        return java.util.List.copyOf(dlqStore);
    }

    public boolean replay(String key) {
        return dlqStore.stream()
                .filter(r -> key.equals(r.key()))
                .findFirst()
                .map(r -> {
                    kafkaTemplate.send(KafkaTopics.RFID_READS_RAW, r.key(), r.event());
                    dlqStore.remove(r);
                    log.info("Replayed DLQ record key={}", key);
                    return true;
                })
                .orElse(false);
    }
}
