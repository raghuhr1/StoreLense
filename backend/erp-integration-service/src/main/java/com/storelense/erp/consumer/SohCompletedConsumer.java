package com.storelense.erp.consumer;

import com.storelense.common.event.SohResultOutboundEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.erp.service.SohResultPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SohCompletedConsumer {

    private final SohResultPushService pushService;

    /**
     * Consumes SOH outbound events and pushes them to ERP.
     * RetryableTopic: 3 retries with exponential backoff before sending to DLT.
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 5_000, multiplier = 2.0, maxDelay = 30_000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = KafkaTopics.DLT_SUFFIX
    )
    @KafkaListener(
            topics = KafkaTopics.ERP_SOH_OUTBOUND,
            groupId = "erp-integration-service"
    )
    public void onSohOutbound(SohResultOutboundEvent event) {
        log.info("Received SOH outbound event: sessionId={} storeId={}",
                event.sessionId(), event.storeId());
        pushService.pushSohResult(event);
    }
}
