package com.storelense.refill.consumer;

import com.storelense.common.event.EpcSoldEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.refill.service.SohAutoReplenishmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EpcSoldConsumer {

    private final SohAutoReplenishmentService autoReplenishmentService;

    @KafkaListener(topics = KafkaTopics.INVENTORY_EPC_SOLD, groupId = "refill-service")
    public void onEpcSold(EpcSoldEvent event) {
        log.info("EPC(s) sold: storeId={} productId={} qty={} — checking live Sales Floor par",
                event.storeId(), event.productId(), event.soldQty());
        autoReplenishmentService.onItemSold(event);
    }
}
