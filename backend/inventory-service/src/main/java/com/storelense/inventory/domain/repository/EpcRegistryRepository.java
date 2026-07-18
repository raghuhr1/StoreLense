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

    @Modifying
    @Query("UPDATE EpcRegistry r SET r.status = 'sold', r.lastSeenAt = :ts " +
           "WHERE r.epc IN :epcs AND r.storeId = :storeId AND r.status = 'in_store'")
    int markSold(@Param("epcs") List<String> epcs,
                 @Param("storeId") UUID storeId,
                 @Param("ts") OffsetDateTime ts);

    List<EpcRegistry> findByEpcInAndStoreId(List<String> epcs, UUID storeId);

    /**
     * Live count of EPCs currently in the given status for a product+zone. Used as the
     * self-correcting source of truth for inventory_state.quantity_on_hand instead of an
     * incrementally-maintained counter, which can drift if a write path double-counts.
     * zoneId is compared with explicit IS NULL semantics since Spring Data's derived
     * equality does not reliably match a bound null parameter across providers.
     */
    @Query("SELECT COUNT(r) FROM EpcRegistry r WHERE r.storeId = :storeId AND r.productId = :productId " +
           "AND ((:zoneId IS NULL AND r.zoneId IS NULL) OR r.zoneId = :zoneId) AND r.status = :status")
    long countLiveOnHand(@Param("storeId") UUID storeId, @Param("productId") UUID productId,
                          @Param("zoneId") UUID zoneId, @Param("status") String status);
}
