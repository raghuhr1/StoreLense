package com.storelense.inventory.service;

import com.storelense.common.event.SohUpdatedEvent;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.inventory.domain.entity.EpcRegistry;
import com.storelense.inventory.domain.entity.InventoryState;
import com.storelense.inventory.domain.repository.EpcRegistryRepository;
import com.storelense.inventory.domain.repository.InventoryStateRepository;
import com.storelense.inventory.dto.SkuInventoryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryStateRepository inventoryStateRepository;
    private final EpcRegistryRepository    epcRegistryRepository;
    private final JdbcClient              jdbcClient;

    @KafkaListener(topics = KafkaTopics.RFID_SOH_UPDATED, groupId = "inventory-service")
    @Transactional
    public void onSohUpdated(SohUpdatedEvent event) {
        if (event.productId() == null) return;

        OffsetDateTime seenAt = OffsetDateTime.ofInstant(event.processedAt(), ZoneOffset.UTC);

        // Upsert EPC registry — marks the EPC as seen at this store+zone
        epcRegistryRepository.findByEpcAndStoreId(event.epc(), event.storeId())
                .ifPresentOrElse(
                        existing -> epcRegistryRepository.updateSighting(
                                event.epc(), event.storeId(), "in_store", seenAt, event.zoneId(), null),
                        () -> epcRegistryRepository.save(EpcRegistry.builder()
                                .epc(event.epc())
                                .storeId(event.storeId())
                                .productId(event.productId())
                                .zoneId(event.zoneId())
                                .status("in_store")
                                .lastSeenAt(seenAt)
                                .build())
                );

        // Increment on-hand count in inventory_state
        inventoryStateRepository.findByStoreIdAndProductIdAndZoneId(
                event.storeId(), event.productId(), event.zoneId())
                .ifPresent(state -> {
                    state.setQuantityOnHand(state.getQuantityOnHand() + 1);
                    state.setLastCountedAt(seenAt);
                    state.setLastSohSessionId(event.sohSessionId());
                    inventoryStateRepository.save(state);
                });
    }

    @Transactional(readOnly = true)
    public List<InventoryState> getStoreInventory(UUID storeId) {
        return inventoryStateRepository.findByStoreId(storeId);
    }

    @Transactional(readOnly = true)
    public List<InventoryState> getLowAccuracyItems(UUID storeId, double threshold) {
        return inventoryStateRepository.findLowAccuracy(storeId, threshold);
    }

    @Transactional(readOnly = true)
    public long countByStatus(UUID storeId, String status) {
        return epcRegistryRepository.countByStoreIdAndStatus(storeId, status);
    }

    /**
     * Upsert the ERP expected quantity for a store × product × zone.
     * Called by the XLS upload tool (POST /api/inventory/expected).
     * Preserves quantity_on_hand from previous RFID scans.
     *
     * @SuppressWarnings: JpaRepository.save() is contractually @NonNull;
     * Eclipse null analysis cannot verify this through generic type parameters.
     */
    @SuppressWarnings("null")
    @Transactional
    public InventoryState upsertExpectedQty(UUID storeId, UUID productId,
                                            UUID zoneId, int quantityExpected) {
        return inventoryStateRepository
                .findByStoreIdAndProductIdAndZoneId(storeId, productId, zoneId)
                .map(existing -> {
                    existing.setQuantityExpected(quantityExpected);
                    existing.setAccuracyPct(calcAccuracy(existing.getQuantityOnHand(), quantityExpected));
                    return inventoryStateRepository.save(existing);
                })
                .orElseGet(() -> inventoryStateRepository.save(
                        InventoryState.builder()
                                .storeId(storeId)
                                .productId(productId)
                                .zoneId(zoneId)
                                .quantityExpected(quantityExpected)
                                .quantityOnHand(0)
                                .build()));
    }

    @Transactional(readOnly = true)
    public SkuInventoryResponse getSkuInventory(String sku, UUID storeId) {
        UUID productId = jdbcClient
                .sql("SELECT id FROM products.products WHERE sku = :sku AND active = true")
                .param("sku", sku)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new ResourceNotFoundException("Product", sku));

        List<InventoryState> states = inventoryStateRepository.findByStoreIdAndProductId(storeId, productId);
        int total = states.stream().mapToInt(InventoryState::getQuantityOnHand).sum();

        List<String> epcs = epcRegistryRepository
                .findByStoreIdAndProductIdAndStatus(storeId, productId, "in_store")
                .stream()
                .map(EpcRegistry::getEpc)
                .toList();

        return new SkuInventoryResponse(sku, productId, storeId, total, 0, total, epcs);
    }

    private java.math.BigDecimal calcAccuracy(int onHand, int expected) {
        if (expected == 0) return java.math.BigDecimal.valueOf(100);
        return java.math.BigDecimal.valueOf(100.0 * onHand / expected)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
