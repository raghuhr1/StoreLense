package com.storelense.soh.domain.repository;

import com.storelense.soh.domain.entity.SohSessionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SohSessionItemRepository extends JpaRepository<SohSessionItem, UUID> {
    Optional<SohSessionItem> findBySession_IdAndProductIdAndZoneId(UUID sessionId, UUID productId, UUID zoneId);

    @Modifying
    @Query("UPDATE SohSessionItem i SET i.countedQuantity = i.countedQuantity + :delta WHERE i.id = :id")
    void incrementCount(@Param("id") UUID id, @Param("delta") int delta);
}
