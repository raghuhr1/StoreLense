package com.storelense.inventory.domain.repository;

import com.storelense.inventory.domain.entity.EpcPositionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EpcPositionHistoryRepository extends JpaRepository<EpcPositionHistory, UUID> {

    List<EpcPositionHistory> findByEpcAndStoreIdOrderByMovedAtDesc(String epc, UUID storeId);

    List<EpcPositionHistory> findBySessionIdOrderByMovedAtDesc(UUID sessionId);
}
