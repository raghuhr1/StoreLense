package com.storelense.inventory.domain.repository;

import com.storelense.inventory.domain.entity.EpcRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EpcRegistryRepository extends JpaRepository<EpcRegistry, UUID> {

    Optional<EpcRegistry> findByEpcAndStoreId(String epc, UUID storeId);

    List<EpcRegistry> findByStoreIdAndStatus(UUID storeId, String status);

    long countByStoreIdAndStatus(UUID storeId, String status);

    List<EpcRegistry> findByStoreIdAndProductIdAndStatus(UUID storeId, UUID productId, String status);

    @Modifying
    @Query("UPDATE EpcRegistry r SET r.status = :status, r.lastSeenAt = :ts, r.zoneId = :zoneId, " +
           "r.lastSeenByReaderId = :readerId WHERE r.epc = :epc AND r.storeId = :storeId")
    void updateSighting(@Param("epc") String epc, @Param("storeId") UUID storeId,
                         @Param("status") String status, @Param("ts") OffsetDateTime ts,
                         @Param("zoneId") UUID zoneId, @Param("readerId") UUID readerId);
}
