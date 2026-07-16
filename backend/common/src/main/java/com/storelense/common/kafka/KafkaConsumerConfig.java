package com.storelense.common.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Shared Kafka consumer configuration imported by individual services.
 * Configures exponential backoff retries (3 attempts) before sending to DLT.
 * Each service imports this and wires its own ConsumerFactory and KafkaTemplate.
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.kafka.core.ConsumerFactory")
public class KafkaConsumerConfig {

    /**
     * Default error handler: retries 3 times with 1 s / 2 s / 4 s backoff,
     * then publishes the failed record to the DLT (topic + ".DLT").
     */
    @Bean
    @ConditionalOnMissingBean(DefaultErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<?, ?> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    // ex is typically a ListenerExecutionFailedException wrapping the real
                    // cause — ex.getMessage() alone only ever printed the generic wrapper
                    // message "Listener failed", never the actual exception or stack trace,
                    // making every DLT'd record permanently undiagnosable. Log the root cause
                    // and pass the full exception so SLF4J prints the stack trace.
                    Throwable root = ex;
                    while (root.getCause() != null && root.getCause() != root) {
                        root = root.getCause();
                    }
                    log.error("Moving record to DLT after exhausted retries. " +
                              "topic={} partition={} offset={} key={} rootCause={}: {}",
                            record.topic(), record.partition(), record.offset(),
                            record.key(), root.getClass().getName(), root.getMessage(), ex);
                    return new org.apache.kafka.common.TopicPartition(
                            KafkaTopics.dlt(record.topic()), -1);
                });

        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxAttempts(3);

        return new DefaultErrorHandler(recoverer, backOff);
    }

    /**
     * Listener container factory wired with the shared error handler.
     * Individual services can override this or create additional factories
     * (e.g., for batch listeners or different concurrency levels).
     */
    @Bean
    @ConditionalOnMissingBean(ConcurrentKafkaListenerContainerFactory.class)
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setObservationEnabled(true); // Micrometer tracing
        return factory;
    }
}
