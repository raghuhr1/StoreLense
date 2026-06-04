package com.storelense.rfid.processing.domain.repository;

import com.storelense.rfid.processing.domain.entity.RfidSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RfidSessionRepository extends JpaRepository<RfidSession, UUID> {

    Optional<RfidSession> findByIdAndStatus(UUID id, String status);

    @Modifying
    @Query("""
            UPDATE RfidSession s
            SET s.totalReadCount = s.totalReadCount + :reads,
                s.uniqueEpcCount = s.uniqueEpcCount + :uniqueEpcs
            WHERE s.id = :id
            """)
    void incrementCounts(@Param("id") UUID id,
                         @Param("reads") int reads,
                         @Param("uniqueEpcs") int uniqueEpcs);

    @Modifying
    @Query("UPDATE RfidSession s SET s.status = 'closed', s.closedAt = :ts WHERE s.id = :id")
    void closeSession(@Param("id") UUID id, @Param("ts") OffsetDateTime ts);
}
