package com.storelense.soh.domain.repository;

import com.storelense.soh.domain.entity.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Page<Transfer> findBySourceStoreIdOrderByCreatedAtDesc(UUID sourceStoreId, Pageable pageable);

    Page<Transfer> findByDestStoreIdOrderByCreatedAtDesc(UUID destStoreId, Pageable pageable);

    Page<Transfer> findBySourceStoreIdAndStatusOrderByCreatedAtDesc(
            UUID sourceStoreId, String status, Pageable pageable);
}
