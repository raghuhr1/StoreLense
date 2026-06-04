package com.storelense.erp.service;

import com.storelense.common.event.ErpProductSyncEvent;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.erp.adapter.ErpClient;
import com.storelense.erp.config.ErpProperties;
import com.storelense.erp.domain.entity.ErpProductMapping;
import com.storelense.erp.domain.entity.ErpSyncLog;
import com.storelense.erp.domain.repository.ErpProductMappingRepository;
import com.storelense.erp.domain.repository.ErpSyncLogRepository;
import com.storelense.erp.dto.ErpProduct;
import com.storelense.erp.dto.ErpProductPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncService {

    private final ErpClient                    erpClient;
    private final ErpProperties                erpProperties;
    private final ErpProductMappingRepository  mappingRepository;
    private final ErpSyncLogRepository         syncLogRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String SYNC_TYPE = "PRODUCT_INBOUND";

    @Scheduled(fixedDelayString = "#{@erpProperties.productSyncInterval().toMillis()}")
    public void scheduledSync() {
        runSync("scheduler");
    }

    @Transactional
    public ErpSyncLog runSync(String triggeredBy) {
        // Guard: only one sync at a time
        if (syncLogRepository.findRunning(SYNC_TYPE).isPresent()) {
            log.warn("Product sync already running — skipping");
            return null;
        }

        ErpSyncLog syncLog = syncLogRepository.save(ErpSyncLog.builder()
                .syncType(SYNC_TYPE)
                .direction("INBOUND")
                .status("running")
                .triggeredBy(triggeredBy)
                .build());

        int fetched = 0, published = 0, failed = 0;
        String cursor = null;

        try {
            do {
                ErpProductPage page = erpClient.fetchProducts(cursor, erpProperties.pageSize());
                if (page == null || page.data() == null) break;

                for (ErpProduct erp : page.data()) {
                    fetched++;
                    try {
                        if (processProduct(erp)) published++;
                    } catch (Exception e) {
                        failed++;
                        log.error("Failed to process ERP product {}: {}", erp.productCode(), e.getMessage());
                    }
                }

                cursor = page.nextCursor();
            } while (cursor != null);

            syncLog.setStatus(failed == 0 ? "completed" : "partial");
            syncLog.setErpCursor(cursor);

        } catch (Exception e) {
            log.error("Product sync failed: {}", e.getMessage(), e);
            syncLog.setStatus("failed");
            syncLog.setErrorMessage(e.getMessage());
        }

        syncLog.setRecordsFetched(fetched);
        syncLog.setRecordsPublished(published);
        syncLog.setRecordsFailed(failed);
        syncLog.setCompletedAt(OffsetDateTime.now());
        syncLogRepository.save(syncLog);

        log.info("Product sync complete: fetched={} published={} failed={} status={}",
                fetched, published, failed, syncLog.getStatus());
        return syncLog;
    }

    /**
     * Returns true if a Kafka event was published (new or changed product).
     */
    private boolean processProduct(ErpProduct erp) {
        String hash = sha256(erp.productCode() + erp.name() + erp.active());

        ErpProductMapping mapping = mappingRepository
                .findByErpProductCode(erp.productCode())
                .orElseGet(() -> ErpProductMapping.builder()
                        .erpProductCode(erp.productCode())
                        .internalSku(erp.sku() != null ? erp.sku() : erp.productCode())
                        .erpProductName(erp.name())
                        .erpCategoryCode(erp.categoryCode())
                        .build());

        // Skip if content hasn't changed
        if (hash.equals(mapping.getSyncHash())) {
            return false;
        }

        mapping.setSyncHash(hash);
        mapping.setErpProductName(erp.name());
        mapping.setErpCategoryCode(erp.categoryCode());
        mapping.setLastSyncedAt(OffsetDateTime.now());
        mappingRepository.save(mapping);

        kafkaTemplate.send(
                KafkaTopics.ERP_PRODUCT_SYNC,
                erp.productCode(),
                new ErpProductSyncEvent(
                        UUID.randomUUID().toString(),
                        erp.productCode(),
                        mapping.getInternalSku(),
                        erp.name(),
                        erp.categoryCode(),
                        erp.brand(),
                        erp.unitOfMeasure(),
                        erp.weightGrams(),
                        erp.rfidEnabled(),
                        erp.active(),
                        Instant.now()
                )
        );
        return true;
    }

    private static String sha256(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }
}
