package com.storelense.rfid.processing.domain.repository;

import com.storelense.rfid.processing.domain.entity.RfidRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface RfidReadRepository extends JpaRepository<RfidRead, Long> {

    @Modifying
    @Query("UPDATE RfidRead r SET r.processed = true, r.processedAt = :ts WHERE r.id = :id")
    void markProcessed(@Param("id") Long id, @Param("ts") OffsetDateTime ts);
}
