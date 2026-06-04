package com.storelense.store.domain.repository;

import com.storelense.store.domain.entity.RfidReader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface RfidReaderRepository extends JpaRepository<RfidReader, UUID> {
    List<RfidReader> findByStoreIdAndActiveTrue(UUID storeId);
    List<RfidReader> findByStoreIdAndZoneId(UUID storeId, UUID zoneId);

    @Modifying
    @Query("UPDATE RfidReader r SET r.lastHeartbeatAt = :ts WHERE r.id = :id")
    void updateHeartbeat(@Param("id") UUID id, @Param("ts") OffsetDateTime ts);
}
