package com.storelense.store.domain.repository;

import com.storelense.store.domain.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    List<Zone> findByStore_IdAndActiveTrueOrderByDisplayOrderAsc(UUID storeId);
    Optional<Zone> findByStore_IdAndZoneCode(UUID storeId, String zoneCode);
    boolean existsByStore_IdAndZoneCode(UUID storeId, String zoneCode);
}
