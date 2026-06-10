package com.storelense.inventory.domain.repository;

import com.storelense.inventory.domain.entity.InventoryState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryStateRepository extends JpaRepository<InventoryState, UUID> {

    List<InventoryState> findByStoreId(UUID storeId);

    List<InventoryState> findByStoreIdAndProductId(UUID storeId, UUID productId);

    Optional<InventoryState> findByStoreIdAndProductIdAndZoneId(UUID storeId, UUID productId, UUID zoneId);

    @Query("SELECT i FROM InventoryState i WHERE i.storeId = :storeId AND i.accuracyPct < :threshold")
    List<InventoryState> findLowAccuracy(@Param("storeId") UUID storeId, @Param("threshold") double threshold);
}
