package com.storelense.erp.service;

import com.storelense.common.event.ErpInventoryExpectedEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.erp.adapter.ErpClient;
import com.storelense.erp.config.ErpProperties;
import com.storelense.erp.domain.entity.ErpStoreMapping;
import com.storelense.erp.domain.entity.ErpSyncLog;
import com.storelense.erp.domain.repository.ErpStoreMappingRepository;
import com.storelense.erp.domain.repository.ErpSyncLogRepository;
import com.storelense.erp.dto.ErpInventoryItem;
import com.storelense.erp.dto.ErpInventoryPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventorySyncService {

    private final ErpClient                    erpClient;
    private final ErpProperties                erpProperties;
    private final ErpStoreMappingRepository    storeMappingRepository;
    private final ErpSyncLogRepository         syncLogRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String SYNC_TYPE = "INVENTORY_INBOUND";

    @Scheduled(cron = "${storelense.erp.inventory-sync-cron:0 0 1 * * *}")
    public void scheduledSync() {
        runSync("scheduler");
    }

    @Transactional
    public ErpSyncLog runSync(String triggeredBy) {
        if (syncLogRepository.findRunning(SYNC_TYPE).isPresent()) {
            log.warn("Inventory sync already running — skipping");
            return null;
        }

        List<ErpStoreMapping> stores = storeMappingRepository.findByInventorySyncEnabledTrue();
        if (stores.isEmpty()) {
            log.info("No stores configured for inventory sync");
            return null;
        }

        ErpSyncLog syncLog = syncLogRepository.save(ErpSyncLog.builder()
                .syncType(SYNC_TYPE)
                .direction("INBOUND")
                .status("running")
                .triggeredBy(triggeredBy)
                .build());

        int fetched = 0, published = 0, failed = 0;

        try {
            for (ErpStoreMapping store : stores) {
                log.info("Syncing inventory for ERP store: {}", store.getErpStoreCode());
                String cursor = null;

                do {
                    ErpInventoryPage page = erpClient.fetchExpectedInventory(
                            store.getErpStoreCode(), cursor, erpProperties.pageSize());
                    if (page == null || page.data() == null) break;

                    for (ErpInventoryItem item : page.data()) {
                        fetched++;
                        try {
                            publishInventoryEvent(item, store);
                            published++;
                        } catch (Exception e) {
                            failed++;
                            log.error("Failed to publish inventory event for {} / {}: {}",
                                    store.getErpStoreCode(), item.productCode(), e.getMessage());
                        }
                    }
                    cursor = page.nextCursor();
                } while (cursor != null);
            }

            syncLog.setStatus(failed == 0 ? "completed" : "partial");

        } catch (Exception e) {
            log.error("Inventory sync failed: {}", e.getMessage(), e);
            syncLog.setStatus("failed");
            syncLog.setErrorMessage(e.getMessage());
        }

        syncLog.setRecordsFetched(fetched);
        syncLog.setRecordsPublished(published);
        syncLog.setRecordsFailed(failed);
        syncLog.setCompletedAt(OffsetDateTime.now());
        syncLogRepository.save(syncLog);

        log.info("Inventory sync complete: fetched={} published={} failed={}", fetched, published, failed);
        return syncLog;
    }

    private void publishInventoryEvent(ErpInventoryItem item, ErpStoreMapping store) {
        kafkaTemplate.send(
                KafkaTopics.ERP_INVENTORY_EXPECTED,
                store.getInternalStoreId().toString(),
                new ErpInventoryExpectedEvent(
                        UUID.randomUUID().toString(),
                        store.getInternalStoreId(),
                        store.getErpStoreCode(),
                        item.productCode(),
                        item.sku(),
                        item.expectedQuantity(),
                        Instant.now()
                )
        );
    }
}
