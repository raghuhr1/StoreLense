package com.storelense.inventory.domain.repository;

import com.storelense.inventory.domain.entity.ZoneScanRollup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ZoneScanRollupRepository extends JpaRepository<ZoneScanRollup, UUID> {

    List<ZoneScanRollup> findByStoreIdAndSessionIdOrderByStatusAscVarianceAsc(UUID storeId, UUID sessionId);

    List<ZoneScanRollup> findByStoreIdAndStatusInOrderByVarianceAsc(UUID storeId, List<String> statuses);
}
