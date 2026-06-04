package com.storelense.store.domain.repository;

import com.storelense.store.domain.entity.Store;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    Optional<Store> findByStoreCode(String storeCode);
    Page<Store> findByActiveTrue(Pageable pageable);
    boolean existsByStoreCode(String storeCode);
}
