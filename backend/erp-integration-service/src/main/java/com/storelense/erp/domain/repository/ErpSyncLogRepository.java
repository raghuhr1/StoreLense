package com.storelense.erp.domain.repository;

import com.storelense.erp.domain.entity.ErpSyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ErpSyncLogRepository extends JpaRepository<ErpSyncLog, UUID> {

    Page<ErpSyncLog> findBySyncTypeOrderByStartedAtDesc(String syncType, Pageable pageable);

    @Query("SELECT l FROM ErpSyncLog l WHERE l.syncType = :type AND l.status = 'completed' ORDER BY l.completedAt DESC")
    Optional<ErpSyncLog> findLastSuccessful(@Param("type") String syncType);

    @Query("SELECT l FROM ErpSyncLog l WHERE l.status = 'running' AND l.syncType = :type")
    Optional<ErpSyncLog> findRunning(@Param("type") String syncType);
}
