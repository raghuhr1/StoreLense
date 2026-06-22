package com.storelense.inventory.event;

import com.storelense.common.event.ErpImportCompletedEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErpImportConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = KafkaTopics.ERP_IMPORT_COMPLETED, groupId = "inventory-service")
    @Transactional
    public void onErpImportCompleted(ErpImportCompletedEvent event) {
        var items = event.resolvedItems();
        if (items == null || items.isEmpty()) {
            log.info("ERP import batch {} has no resolved items — skipping inventory expected update", event.batchId());
            return;
        }

        int updated = 0;
        for (var item : items) {
            try {
                inventoryService.upsertExpectedQty(
                        event.storeId(),
                        item.productId(),
                        null,           // store-level: no zone breakdown from ERP
                        item.expectedQty()
                );
                updated++;
            } catch (Exception e) {
                log.warn("Failed to upsert expected qty for product {} store {}: {}",
                        item.productId(), event.storeId(), e.getMessage());
            }
        }
        log.info("ERP import batch {}: set quantity_expected for {}/{} products at store {}",
                event.batchId(), updated, items.size(), event.storeId());
    }
}
