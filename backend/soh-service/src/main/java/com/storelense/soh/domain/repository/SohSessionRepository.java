package com.storelense.soh.domain.repository;

import com.storelense.soh.domain.entity.SohSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SohSessionRepository extends JpaRepository<SohSession, UUID> {

    // Serializes concurrent completeSession() calls for the same session (e.g. a duplicate
    // "Finish Zone" network retry) so only one can transition status and insert SohResult.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SohSession s WHERE s.id = :id")
    Optional<SohSession> findByIdForUpdate(@Param("id") UUID id);

    Page<SohSession> findByStoreIdOrderByStartedAtDesc(UUID storeId, Pageable pageable);
    Page<SohSession> findByStoreIdAndStatusInOrderByStartedAtDesc(UUID storeId, List<String> statuses, Pageable pageable);

    @Query("SELECT s FROM SohSession s WHERE s.storeId = :storeId AND s.status IN ('created','in_progress','paused') ORDER BY s.startedAt DESC")
    List<SohSession> findActiveSessions(@Param("storeId") UUID storeId);

    List<SohSession> findByCycleCountIdOrderByStartedAtAsc(UUID cycleCountId);

    @Query("SELECT s FROM SohSession s WHERE s.storeId = :storeId AND s.status IN ('in_progress','paused') AND s.startedAt < :cutoff")
    List<SohSession> findStaleActiveSessions(@Param("storeId") UUID storeId,
                                              @Param("cutoff") OffsetDateTime cutoff);

    @Query("SELECT s FROM SohSession s WHERE s.status IN ('in_progress','paused') AND s.startedAt < :cutoff")
    List<SohSession> findAllStaleActiveSessions(@Param("cutoff") OffsetDateTime cutoff);
}
