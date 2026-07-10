package com.storelense.store.domain.repository;

import com.storelense.store.domain.entity.StoreFeature;
import com.storelense.store.domain.entity.StoreFeatureId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StoreFeatureRepository extends JpaRepository<StoreFeature, StoreFeatureId> {

    List<StoreFeature> findByStoreId(UUID storeId);

    void deleteByStoreId(UUID storeId);
}
