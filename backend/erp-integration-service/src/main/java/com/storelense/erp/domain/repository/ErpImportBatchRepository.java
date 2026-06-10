package com.storelense.erp.domain.repository;

import com.storelense.erp.domain.entity.ErpImportBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ErpImportBatchRepository extends JpaRepository<ErpImportBatch, UUID> {

    Page<ErpImportBatch> findByStoreIdOrderByCreatedAtDesc(UUID storeId, Pageable pageable);

    Optional<ErpImportBatch> findTopByStoreIdAndStatusOrderByCreatedAtDesc(UUID storeId, String status);

    // Returns true when a non-FAILED batch for this path already exists (S3 dedup check)
    boolean existsByFilePathAndStatusNot(String filePath, String status);
}
