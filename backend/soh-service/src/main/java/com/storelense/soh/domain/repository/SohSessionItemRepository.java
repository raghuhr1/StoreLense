package com.storelense.soh.domain.repository;

import com.storelense.soh.domain.entity.SohSessionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SohSessionItemRepository extends JpaRepository<SohSessionItem, UUID> {

    Optional<SohSessionItem> findBySession_IdAndProductIdAndZoneId(UUID sessionId, UUID productId, UUID zoneId);

    Optional<SohSessionItem> findBySession_IdAndProductIdAndZoneIdAndLocationCode(
            UUID sessionId, UUID productId, UUID zoneId, String locationCode);

    List<SohSessionItem> findBySession_Id(UUID sessionId);

    @Modifying
    @Query("UPDATE SohSessionItem i SET i.countedQuantity = i.countedQuantity + :delta WHERE i.id = :id")
    void incrementCount(@Param("id") UUID id, @Param("delta") int delta);

    @Query("""
           SELECT COALESCE(SUM(i.countedQuantity), 0)
           FROM SohSessionItem i
           WHERE i.session.id = :sessionId AND i.locationCode = :locationCode
           """)
    int sumCountedByLocation(@Param("sessionId") UUID sessionId,
                              @Param("locationCode") String locationCode);

    @Query("""
           SELECT COALESCE(SUM(i.expectedQuantity), 0)
           FROM SohSessionItem i
           WHERE i.session.id = :sessionId AND i.locationCode = :locationCode
           """)
    int sumExpectedByLocation(@Param("sessionId") UUID sessionId,
                               @Param("locationCode") String locationCode);
}
