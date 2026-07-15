package com.storelense.product.domain.repository;

import com.storelense.product.domain.entity.Barcode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BarcodeRepository extends JpaRepository<Barcode, UUID> {
    Optional<Barcode> findByProduct_IdAndBarcodeType(UUID productId, String barcodeType);

    @Query("SELECT COUNT(b) > 0 FROM Barcode b WHERE UPPER(b.barcodeValue) = UPPER(:barcodeValue)")
    boolean existsByBarcodeValueIgnoreCase(@Param("barcodeValue") String barcodeValue);
}
