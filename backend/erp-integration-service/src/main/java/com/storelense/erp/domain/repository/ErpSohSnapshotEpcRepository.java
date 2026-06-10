package com.storelense.erp.domain.repository;

import com.storelense.erp.domain.entity.ErpSohSnapshotEpc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ErpSohSnapshotEpcRepository extends JpaRepository<ErpSohSnapshotEpc, UUID> {

    List<ErpSohSnapshotEpc> findBySnapshot_Id(UUID snapshotId);

    List<ErpSohSnapshotEpc> findByEpc(String epc);

    long countBySnapshot_Id(UUID snapshotId);

    // Fetch resolved EPCs for a batch, optionally scoped to a zone region.
    // Passing null for zoneRegion returns all zones (full-store session).
    @Query("SELECT e FROM ErpSohSnapshotEpc e JOIN FETCH e.snapshot s " +
           "WHERE s.batch.id = :batchId " +
           "AND (:zoneRegion IS NULL OR s.zoneRegion = :zoneRegion) " +
           "AND s.resolutionStatus = 'RESOLVED'")
    List<ErpSohSnapshotEpc> findByBatchAndZone(@Param("batchId") UUID batchId,
                                               @Param("zoneRegion") String zoneRegion);
}
