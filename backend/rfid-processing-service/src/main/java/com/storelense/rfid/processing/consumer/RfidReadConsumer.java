package com.storelense.rfid.processing.consumer;

import com.storelense.common.event.RfidReadEvent;
import com.storelense.common.event.SohUpdatedEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.rfid.processing.domain.entity.RfidRead;
import com.storelense.rfid.processing.domain.repository.RfidReadRepository;
import com.storelense.rfid.processing.service.EpcResolutionService;
import com.storelense.rfid.processing.service.RfidProcessingMetrics;
import com.storelense.rfid.processing.service.RfidSessionManager;
import com.storelense.rfid.processing.service.ZoneMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RfidReadConsumer {

    private final RfidReadRepository    readRepository;
    private final EpcResolutionService  epcResolution;
    private final RfidSessionManager    sessionManager;
    private final ZoneMappingService    zoneMappingService;
    private final RfidProcessingMetrics metrics;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Retries 3 times with 500 ms → 1 s → 2 s backoff before routing to DLT.
     * Concurrency=4 defined in application.yml keeps up with 50k reads/sec burst.
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5_000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = KafkaTopics.DLT_SUFFIX,
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = KafkaTopics.RFID_READS_RAW,
            groupId = "rfid-processing-service",
            concurrency = "${spring.kafka.listener.concurrency:4}"
    )
    @Transactional
    public void consume(RfidReadEvent event) {
        long startNs = System.nanoTime();
        metrics.recordEventReceived();

        try {
            // 1. Session-level deduplication via Redis HLL
            boolean isNewEpc = sessionManager.recordRead(event.rfidSessionId(), event.epc());
            if (!isNewEpc) {
                metrics.recordEventDeduped();
                log.trace("Duplicate EPC {} in session {} — skipped", event.epc(), event.rfidSessionId());
                return;
            }

            // 2. Persist the raw read
            RfidRead saved = persistRead(event);

            // 3. Resolve EPC → product UUID (cache → API → GTIN decode)
            Optional<UUID> productId = epcResolution.resolveToProductId(event.epc());
            if (productId.isEmpty()) {
                metrics.recordResolutionFailed();
                readRepository.markProcessed(saved.getId(), OffsetDateTime.now());
                log.debug("EPC {} unresolved — persisted but no SOH event published", event.epc());
                return;
            }

            // 4. Resolve reader → zone
            Optional<UUID> zoneId = zoneMappingService.resolveZone(event.readerId());

            // 5. Publish rfid.soh.updated
            SohUpdatedEvent sohEvent = new SohUpdatedEvent(
                    UUID.randomUUID().toString(),
                    event.rfidSessionId(),
                    null,                   // sohSessionId correlated by soh-service
                    event.storeId(),
                    productId.get(),
                    zoneId.orElse(null),
                    event.epc(),
                    Instant.now()
            );

            kafkaTemplate.send(KafkaTopics.RFID_SOH_UPDATED, event.storeId().toString(), sohEvent);
            metrics.recordSohEventPublished();

            readRepository.markProcessed(saved.getId(), OffsetDateTime.now());
            metrics.recordEventProcessed();

        } catch (Exception ex) {
            metrics.recordProcessingError();
            log.error("Processing failed for event={} epc={}: {}",
                    event.eventId(), event.epc(), ex.getMessage(), ex);
            throw ex;   // triggers RetryableTopic retry → DLT
        } finally {
            metrics.recordProcessingTime(System.nanoTime() - startNs);
        }
    }

    private RfidRead persistRead(RfidReadEvent event) {
        return readRepository.save(RfidRead.builder()
                .rfidSessionId(event.rfidSessionId())
                .storeId(event.storeId())
                .readerId(event.readerId())
                .epc(event.epc())
                .rssi(event.rssi())
                .antennaPort(event.antennaPort() != null ? event.antennaPort().shortValue() : null)
                .readAt(OffsetDateTime.ofInstant(event.readAt(), ZoneOffset.UTC))
                .build());
    }
}
