package com.storelense.soh.domain.repository;

import com.storelense.soh.domain.entity.StoreLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoreLocationRepository extends JpaRepository<StoreLocation, UUID> {

    List<StoreLocation> findByStoreIdAndIsActiveTrueOrderBySortOrderAsc(UUID storeId);

    Optional<StoreLocation> findByStoreIdAndLocationCodeAndSectionCode(
            UUID storeId, String locationCode, String sectionCode);

    boolean existsByStoreIdAndLocationCodeAndSectionCodeAndIsActiveTrue(
            UUID storeId, String locationCode, String sectionCode);
}
