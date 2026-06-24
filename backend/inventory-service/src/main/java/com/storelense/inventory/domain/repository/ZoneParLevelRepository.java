package com.storelense.inventory.domain.repository;

import com.storelense.inventory.domain.entity.ZoneParLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneParLevelRepository extends JpaRepository<ZoneParLevel, UUID> {

    List<ZoneParLevel> findByStoreIdAndActiveTrue(UUID storeId);

    List<ZoneParLevel> findByStoreIdAndZoneIdAndActiveTrue(UUID storeId, UUID zoneId);

    Optional<ZoneParLevel> findByStoreIdAndZoneIdAndProductId(UUID storeId, UUID zoneId, UUID productId);

    List<ZoneParLevel> findByStoreIdAndProductIdAndActiveTrue(UUID storeId, UUID productId);
}
