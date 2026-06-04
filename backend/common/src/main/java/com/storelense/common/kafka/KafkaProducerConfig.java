package com.storelense.common.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;
import static java.util.Map.entry;

/**
 * Shared Kafka producer configuration.
 * Idempotent producer with acks=all and max.in.flight=5 ensures
 * exactly-once delivery semantics within a single producer session.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.ProducerFactory")
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    @ConditionalOnMissingBean(ProducerFactory.class)
    public ProducerFactory<String, Object> producerFactory() {
        return new DefaultKafkaProducerFactory<>(Map.ofEntries(
                entry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,              bootstrapServers),
                entry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,           "org.apache.kafka.common.serialization.StringSerializer"),
                entry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,         JsonSerializer.class),
                entry(ProducerConfig.ACKS_CONFIG,                           "all"),
                entry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,             true),
                entry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5),
                entry(ProducerConfig.RETRIES_CONFIG,                        Integer.MAX_VALUE),
                entry(ProducerConfig.COMPRESSION_TYPE_CONFIG,               "lz4"),
                entry(ProducerConfig.LINGER_MS_CONFIG,                      5),
                entry(ProducerConfig.BATCH_SIZE_CONFIG,                     65536),
                entry(JsonSerializer.ADD_TYPE_INFO_HEADERS,                 false)
        ));
    }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<?, ?> kafkaTemplate(ProducerFactory<String, Object> pf) {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(pf);
        template.setObservationEnabled(true);
        return template;
    }
}
