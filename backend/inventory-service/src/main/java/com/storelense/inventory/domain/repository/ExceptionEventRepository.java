package com.storelense.inventory.domain.repository;

import com.storelense.inventory.domain.entity.ExceptionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExceptionEventRepository extends JpaRepository<ExceptionEvent, UUID> {

    Page<ExceptionEvent> findByStoreIdAndTypeOrderByCreatedAtDesc(
            UUID storeId, String type, Pageable pageable);

    long countByStoreIdAndTypeAndStatusIn(UUID storeId, String type, List<String> statuses);

    Optional<ExceptionEvent> findFirstByEpcAndStoreIdAndTypeAndStatusInOrderByCreatedAtDesc(
            String epc, UUID storeId, String type, List<String> statuses);
}
