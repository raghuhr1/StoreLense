package com.storelense.erp.domain.repository;

import com.storelense.erp.domain.entity.ErpStoreMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ErpStoreMappingRepository extends JpaRepository<ErpStoreMapping, UUID> {
    Optional<ErpStoreMapping> findByErpStoreCode(String erpStoreCode);
    Optional<ErpStoreMapping> findByInternalStoreId(UUID storeId);
    List<ErpStoreMapping> findByInventorySyncEnabledTrue();
    List<ErpStoreMapping> findBySohPushEnabledTrue();
}
