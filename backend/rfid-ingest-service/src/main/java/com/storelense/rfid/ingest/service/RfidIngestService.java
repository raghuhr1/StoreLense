package com.storelense.rfid.ingest.service;

import com.storelense.common.event.RfidReadEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.rfid.ingest.dto.RfidReadBatchRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RfidIngestService {

    private final KafkaTemplate<String, RfidReadEvent> kafkaTemplate;
    private final StringRedisTemplate                  redis;

    private static final Duration DEDUP_TTL = Duration.ofHours(25);

    @SuppressWarnings("null") // Duration.ofHours() and UUID.toString() are non-null; Eclipse can't verify through generic type params
    public int ingestBatch(RfidReadBatchRequest batch, String correlationId) {
        int published = 0;

        for (var entry : batch.reads()) {
            String upperEpc = entry.epc().toUpperCase();
            String dedupKey = "rfid:dedup:" + batch.rfidSessionId() + ":" + upperEpc;

            // Deduplicate within session using Redis SET
            Boolean isNew = redis.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);
            if (Boolean.FALSE.equals(isNew)) {
                continue; // already seen this EPC in this session
            }

            RfidReadEvent event = new RfidReadEvent(
                    UUID.randomUUID().toString(),
                    batch.rfidSessionId(),
                    batch.storeId(),
                    batch.readerId(),
                    upperEpc,
                    entry.rssi(),
                    entry.antennaPort(),
                    entry.readAt() != null ? entry.readAt() : Instant.now(),
                    correlationId
            );

            // Fire-and-forget: log failures asynchronously but don't block the response
            kafkaTemplate.send(KafkaTopics.RFID_READS_RAW, batch.storeId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish RFID read event for EPC {}: {}", upperEpc, ex.getMessage());
                        }
                    });

            published++; // count reads accepted and submitted (before async Kafka ack)
        }

        return published;
    }
}
