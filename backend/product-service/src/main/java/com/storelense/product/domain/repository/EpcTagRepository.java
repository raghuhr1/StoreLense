package com.storelense.product.domain.repository;

import com.storelense.product.domain.entity.EpcTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EpcTagRepository extends JpaRepository<EpcTag, UUID> {
    Optional<EpcTag> findByEpc(String epc);
    List<EpcTag> findByProduct_IdAndActiveTrue(UUID productId);
    boolean existsByEpc(String epc);
    long countByProduct_IdAndActiveTrue(UUID productId);
}
