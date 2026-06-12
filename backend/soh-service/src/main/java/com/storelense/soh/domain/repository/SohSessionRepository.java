package com.storelense.soh.domain.repository;

import com.storelense.soh.domain.entity.SohSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SohSessionRepository extends JpaRepository<SohSession, UUID> {
    Page<SohSession> findByStoreIdOrderByStartedAtDesc(UUID storeId, Pageable pageable);
    Page<SohSession> findByStoreIdAndStatusInOrderByStartedAtDesc(UUID storeId, List<String> statuses, Pageable pageable);

    @Query("SELECT s FROM SohSession s WHERE s.storeId = :storeId AND s.status IN ('created','in_progress') ORDER BY s.startedAt DESC")
    Optional<SohSession> findActiveSession(@Param("storeId") UUID storeId);
}
