package com.storelense.rfid.processing.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * High-throughput Kafka configuration for the RFID processing pipeline.
 *
 * Key tuning:
 * - 4 consumer threads (matches topic partition count ÷ 3 replicas)
 * - max.poll.records=500 to batch-process and reduce DB round-trips
 * - fetch.min.bytes=64KB to avoid single-record fetches under load
 * - enable.auto.commit=false → RECORD ack mode for at-least-once ordering
 */
@Configuration
public class KafkaProcessingConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,          bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,                   "rfid-processing-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,     StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,   JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,          "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,         false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,           500);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG,            65536);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,          100);
        props.put(JsonDeserializer.TRUSTED_PACKAGES,                "com.storelense.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS,           false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                  "com.storelense.common.event.RfidReadEvent");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,                  bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,               StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,             JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG,                               "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,                 true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,     5);
        props.put(ProducerConfig.LINGER_MS_CONFIG,                          2);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG,                         65536);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS,                     false);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        KafkaTemplate<String, Object> t = new KafkaTemplate<>(pf);
        t.setObservationEnabled(true);
        return t;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> cf) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setConcurrency(4);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
