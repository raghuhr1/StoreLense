package com.storelense.inventory.domain.repository;

import com.storelense.inventory.domain.entity.StoreLocationParLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreLocationParLevelRepository extends JpaRepository<StoreLocationParLevel, UUID> {

    List<StoreLocationParLevel> findByStoreIdAndActiveTrue(UUID storeId);

    List<StoreLocationParLevel> findByStoreIdAndLocationCodeAndActiveTrue(UUID storeId, String locationCode);

    Optional<StoreLocationParLevel> findByStoreIdAndLocationCodeAndProductId(
            UUID storeId, String locationCode, UUID productId);
}
