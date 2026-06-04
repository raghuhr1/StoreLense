package com.storelense.refill.domain.repository;

import com.storelense.refill.domain.entity.RefillTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RefillTaskRepository extends JpaRepository<RefillTask, UUID> {
    Page<RefillTask> findByStoreIdOrderByPriorityAscCreatedAtDesc(UUID storeId, Pageable pageable);
    Page<RefillTask> findByStoreIdAndStatusOrderByPriorityAscCreatedAtDesc(UUID storeId, String status, Pageable pageable);
    long countByStoreIdAndStatusIn(UUID storeId, java.util.List<String> statuses);
}
