package com.storelense.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares all Kafka topics with their replication and partition settings.
 * Spring Boot auto-creates these via AdminClient on startup if they don't exist.
 * Import this configuration in any service that needs topic auto-creation.
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.config.TopicBuilder")
public class KafkaTopicProvisioner {

    // High-throughput RFID pipeline: 12 partitions to match Kafka consumer concurrency
    @Bean NewTopic rfidReadsRaw() {
        return TopicBuilder.name(KafkaTopics.RFID_READS_RAW)
                .partitions(12).replicas(1)
                .config("retention.ms", "86400000")       // 24 h
                .config("compression.type", "lz4")
                .build();
    }

    @Bean NewTopic rfidSohUpdated() {
        return TopicBuilder.name(KafkaTopics.RFID_SOH_UPDATED)
                .partitions(12).replicas(1)
                .config("retention.ms", "86400000")
                .build();
    }

    @Bean NewTopic rfidReaderHeartbeat() {
        return TopicBuilder.name(KafkaTopics.RFID_READER_HEARTBEAT)
                .partitions(3).replicas(1)
                .config("retention.ms", "3600000")        // 1 h (heartbeats are ephemeral)
                .build();
    }

    @Bean NewTopic sohSessionCompleted() {
        return TopicBuilder.name(KafkaTopics.SOH_SESSION_COMPLETED)
                .partitions(6).replicas(1)
                .config("retention.ms", "604800000")      // 7 days
                .build();
    }

    @Bean NewTopic refillTaskCreated() {
        return TopicBuilder.name(KafkaTopics.REFILL_TASK_CREATED)
                .partitions(6).replicas(1)
                .config("retention.ms", "604800000")
                .build();
    }

    @Bean NewTopic refillTaskCompleted() {
        return TopicBuilder.name(KafkaTopics.REFILL_TASK_COMPLETED)
                .partitions(6).replicas(1)
                .config("retention.ms", "604800000")
                .build();
    }

    @Bean NewTopic erpProductSync() {
        return TopicBuilder.name(KafkaTopics.ERP_PRODUCT_SYNC)
                .partitions(3).replicas(1)
                .config("retention.ms", "604800000")
                .build();
    }

    @Bean NewTopic erpInventoryExpected() {
        return TopicBuilder.name(KafkaTopics.ERP_INVENTORY_EXPECTED)
                .partitions(6).replicas(1)
                .config("retention.ms", "172800000")      // 2 days
                .build();
    }

    @Bean NewTopic erpSohOutbound() {
        return TopicBuilder.name(KafkaTopics.ERP_SOH_OUTBOUND)
                .partitions(6).replicas(1)
                .config("retention.ms", "604800000")
                .build();
    }

    // Dead-letter topics
    @Bean NewTopic rfidReadsRawDlt() {
        return TopicBuilder.name(KafkaTopics.dlt(KafkaTopics.RFID_READS_RAW))
                .partitions(3).replicas(1)
                .config("retention.ms", "2592000000")     // 30 days for investigation
                .build();
    }

    @Bean NewTopic sohSessionCompletedDlt() {
        return TopicBuilder.name(KafkaTopics.dlt(KafkaTopics.SOH_SESSION_COMPLETED))
                .partitions(3).replicas(1)
                .config("retention.ms", "2592000000")
                .build();
    }

    @Bean NewTopic erpProductSyncDlt() {
        return TopicBuilder.name(KafkaTopics.dlt(KafkaTopics.ERP_PRODUCT_SYNC))
                .partitions(3).replicas(1)
                .config("retention.ms", "2592000000")
                .build();
    }
}
