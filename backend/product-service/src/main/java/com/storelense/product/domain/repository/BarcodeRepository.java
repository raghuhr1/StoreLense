package com.storelense.product.domain.repository;

import com.storelense.product.domain.entity.Barcode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BarcodeRepository extends JpaRepository<Barcode, UUID> {
    Optional<Barcode> findByProduct_IdAndBarcodeType(UUID productId, String barcodeType);
    boolean existsByBarcodeValue(String barcodeValue);
}
