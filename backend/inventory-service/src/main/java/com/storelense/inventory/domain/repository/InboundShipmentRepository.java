package com.storelense.inventory.domain.repository;

import com.storelense.inventory.domain.entity.InboundShipment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InboundShipmentRepository extends JpaRepository<InboundShipment, UUID> {
    Page<InboundShipment> findByStoreIdOrderByExpectedAtDesc(UUID storeId, Pageable pageable);
    Page<InboundShipment> findByStoreIdAndStatusInOrderByExpectedAtDesc(UUID storeId, List<String> statuses, Pageable pageable);
}
