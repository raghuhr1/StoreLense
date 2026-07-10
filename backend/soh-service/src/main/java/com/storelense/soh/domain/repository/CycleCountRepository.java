package com.storelense.soh.domain.repository;

import com.storelense.soh.domain.entity.CycleCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CycleCountRepository extends JpaRepository<CycleCount, UUID> {

    Page<CycleCount> findByStoreIdOrderByCountDateDescCreatedAtDesc(UUID storeId, Pageable pageable);

    @Query("SELECT c FROM CycleCount c WHERE c.storeId = :storeId AND c.status IN :statuses ORDER BY c.countDate DESC")
    List<CycleCount> findActiveByStore(@Param("storeId") UUID storeId,
                                       @Param("statuses") List<String> statuses);
}
