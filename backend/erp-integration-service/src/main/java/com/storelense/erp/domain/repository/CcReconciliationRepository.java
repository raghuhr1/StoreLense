package com.storelense.erp.domain.repository;

import com.storelense.erp.domain.entity.CcReconciliation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CcReconciliationRepository extends JpaRepository<CcReconciliation, UUID> {

    Optional<CcReconciliation> findTopBySessionIdOrderByRunAtDesc(UUID sessionId);

    List<CcReconciliation> findBySessionIdOrderByRunAtDesc(UUID sessionId);

    List<CcReconciliation> findByBatchIdOrderByRunAtDesc(UUID batchId);

    Page<CcReconciliation> findByStoreIdOrderByRunAtDesc(UUID storeId, Pageable pageable);

    List<CcReconciliation> findByCycleCountIdOrderByRunAtDesc(UUID cycleCountId);

    Optional<CcReconciliation> findTopByCycleCountIdOrderByRunAtDesc(UUID cycleCountId);
}
