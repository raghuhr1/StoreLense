package com.storelense.erp.domain.repository;

import com.storelense.erp.domain.entity.ErpSohSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ErpSohSnapshotRepository extends JpaRepository<ErpSohSnapshot, UUID> {

    List<ErpSohSnapshot> findByBatch_Id(UUID batchId);

    List<ErpSohSnapshot> findByBatch_IdAndResolutionStatus(UUID batchId, String resolutionStatus);

    long countByBatch_IdAndResolutionStatus(UUID batchId, String resolutionStatus);

    @Query("SELECT COUNT(s) FROM ErpSohSnapshot s WHERE s.batch.id = :batchId AND s.resolutionStatus <> 'RESOLVED'")
    long countUnresolvedByBatchId(@Param("batchId") UUID batchId);
}
