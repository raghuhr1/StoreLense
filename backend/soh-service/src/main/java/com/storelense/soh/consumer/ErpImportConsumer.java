package com.storelense.soh.consumer;

import com.storelense.common.event.ErpImportCompletedEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.soh.service.SohSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErpImportConsumer {

    private final SohSessionService sohSessionService;

    @KafkaListener(topics = KafkaTopics.ERP_IMPORT_COMPLETED, groupId = "soh-service")
    public void onErpImportCompleted(ErpImportCompletedEvent event) {
        log.info("ERP import completed: batchId={} storeId={} zoneRegion={}",
                event.batchId(), event.storeId(), event.zoneRegion());
        var session = sohSessionService.createFromErpImport(event);
        log.info("Created erp_triggered SOH session {} for batch {}", session.id(), event.batchId());
    }
}
