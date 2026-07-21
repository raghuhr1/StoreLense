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

    // Sum of ERP-expected-but-not-yet-scanned units across every product/zone at this
    // store — distinct from epc_registry's 'missing' status, which only reflects EPCs a
    // manager has explicitly flagged via the Exceptions "mark missing" action. Most
    // shortfall (expected but never physically scanned this cycle) never gets that
    // manual flag, so the Stock Levels "Missing EPC" card showed 0 even when hundreds
    // of ERP-expected units were unaccounted for.
    @Query("""
            SELECT COALESCE(SUM(GREATEST(i.quantityExpected - i.quantityOnHand, 0)), 0)
            FROM InventoryState i
            WHERE i.storeId = :storeId
            """)
    long sumUnscannedExpected(@Param("storeId") UUID storeId);
}
