package com.storelense.erp.domain.repository;

import com.storelense.erp.domain.entity.ErpProductMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ErpProductMappingRepository extends JpaRepository<ErpProductMapping, UUID> {
    Optional<ErpProductMapping> findByErpProductCode(String erpProductCode);
    Optional<ErpProductMapping> findByInternalSku(String sku);
    boolean existsByErpProductCode(String erpProductCode);
}
