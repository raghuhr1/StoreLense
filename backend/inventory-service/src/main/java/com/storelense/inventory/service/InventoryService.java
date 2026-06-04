package com.storelense.inventory.service;

import com.storelense.common.event.SohUpdatedEvent;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.common.kafka.KafkaTopics;
import com.storelense.inventory.domain.entity.EpcRegistry;
import com.storelense.inventory.domain.entity.InventoryState;
import com.storelense.inventory.domain.repository.EpcRegistryRepository;
import com.storelense.inventory.domain.repository.InventoryStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
