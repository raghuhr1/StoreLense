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
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class RfidIngestService {

    private final KafkaTemplate<String, RfidReadEvent> kafkaTemplate;
    private final StringRedisTemplate                  redis;

    private static final Duration DEDUP_TTL = Duration.ofHours(25);

    public int ingestBatch(RfidReadBatchRequest batch, String correlationId) {
        AtomicInteger published = new AtomicInteger(0);

        batch.reads().forEach(entry -> {
            String upperEpc = entry.epc().toUpperCase();
            String dedupKey = "rfid:dedup:" + batch.rfidSessionId() + ":" + upperEpc;

            // Deduplicate within session using Redis SET
            Boolean isNew = redis.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_TTL);
            if (Boolean.FALSE.equals(isNew)) {
                return; // already seen this EPC in this session
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

            kafkaTemplate.send(KafkaTopics.RFID_READS_RAW, batch.storeId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish RFID read event for EPC {}: {}", upperEpc, ex.getMessage());
                        } else {
                            published.incrementAndGet();
                        }
                    });
        });

        return published.get();
    }
}
