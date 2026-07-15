package com.storelense.product.domain.repository;

import com.storelense.product.domain.entity.EpcTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EpcTagRepository extends JpaRepository<EpcTag, UUID> {
    Optional<EpcTag> findByEpc(String epc);
    List<EpcTag> findByProduct_IdAndActiveTrue(UUID productId);
    boolean existsByEpc(String epc);
    long countByProduct_IdAndActiveTrue(UUID productId);

    @Query("SELECT e FROM EpcTag e JOIN e.product p JOIN p.barcodes b WHERE UPPER(b.barcodeValue) = UPPER(:ean) AND e.active = true")
    List<EpcTag> findActiveByBarcodeValue(@Param("ean") String ean);
}
