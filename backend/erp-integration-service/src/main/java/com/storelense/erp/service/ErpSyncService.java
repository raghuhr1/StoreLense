package com.storelense.erp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErpSyncService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestClient                    erpClient;

    // Product master sync — every 6 hours
    @Scheduled(fixedDelayString = "${storelense.erp.product-sync-interval-ms:21600000}")
    public void syncProducts() {
        log.info("Starting ERP product sync at {}", Instant.now());
        try {
            // In real implementation: call ERP REST/EDI endpoint, transform, publish to Kafka
            // kafkaTemplate.send("erp.product.sync", erpProductId, transformedProduct);
            log.info("ERP product sync completed");
        } catch (Exception e) {
            log.error("ERP product sync failed: {}", e.getMessage(), e);
        }
    }

    // Inventory expected quantity sync — nightly
    @Scheduled(cron = "0 0 1 * * *")
    public void syncExpectedInventory() {
        log.info("Starting ERP inventory expected sync at {}", Instant.now());
        try {
            // In real implementation: pull expected quantities from ERP per store
            // publish to Kafka topic erp.inventory.expected
            log.info("ERP inventory sync completed");
        } catch (Exception e) {
            log.error("ERP inventory sync failed: {}", e.getMessage(), e);
        }
    }

    // Push SOH results back to ERP — triggered by soh.session.completed events
    public void pushSohResultToErp(UUID storeId, UUID sessionId, Map<String, Object> payload) {
        log.info("Pushing SOH result to ERP for store {} session {}", storeId, sessionId);
        try {
            erpClient.post()
                    .uri("/api/inventory/soh-result")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Failed to push SOH result to ERP: {}", e.getMessage());
        }
    }
}
