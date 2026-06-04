package com.storelense.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Base class for DLT (Dead Letter Topic) consumers.
 * Services extend this to handle failed records from their topics.
 * Provides helper to re-publish to the original topic after manual fix.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class DlqReprocessingSupport {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Implement this to inspect and optionally fix the failed record before replay.
     * Return true to republish to the original topic, false to discard.
     */
    protected abstract boolean shouldReprocess(ConsumerRecord<String, Object> record, Exception exception);

    /**
     * Re-publishes a DLT record back to its original topic.
     * The original topic name is the DLT name minus the ".DLT" suffix.
     */
    protected void replayToOriginalTopic(ConsumerRecord<String, Object> record) {
        String originalTopic = record.topic().replace(KafkaTopics.DLT_SUFFIX, "");
        log.info("Replaying record to topic={} key={}", originalTopic, record.key());
        kafkaTemplate.send(originalTopic, record.key().toString(), record.value())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Replay failed for topic={} key={}: {}", originalTopic, record.key(), ex.getMessage());
                    } else {
                        log.info("Replayed record to topic={} key={} partition={} offset={}",
                                originalTopic, record.key(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
