package com.storelense.erp.domain.repository;

import com.storelense.erp.domain.entity.CcReconciliationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CcReconciliationItemRepository extends JpaRepository<CcReconciliationItem, UUID> {

    List<CcReconciliationItem> findByReconciliation_Id(UUID reconciliationId);

    List<CcReconciliationItem> findByReconciliation_IdAndStatus(UUID reconciliationId, String status);
}
