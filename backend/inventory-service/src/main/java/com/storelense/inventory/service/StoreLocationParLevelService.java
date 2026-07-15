package com.storelense.inventory.service;

import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.inventory.domain.entity.StoreLocationParLevel;
import com.storelense.inventory.domain.repository.StoreLocationParLevelRepository;
import com.storelense.inventory.dto.StoreLocationParLevelRequest;
import com.storelense.inventory.dto.StoreLocationParLevelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreLocationParLevelService {

    private final StoreLocationParLevelRepository repository;

    @Transactional(readOnly = true)
    public List<StoreLocationParLevelResponse> list(UUID storeId, String locationCode) {
        List<StoreLocationParLevel> rows = (locationCode != null)
                ? repository.findByStoreIdAndLocationCodeAndActiveTrue(storeId, locationCode)
                : repository.findByStoreIdAndActiveTrue(storeId);
        return rows.stream().map(StoreLocationParLevelResponse::from).toList();
    }

    @SuppressWarnings("null")
    @Transactional
    public StoreLocationParLevelResponse upsert(UUID storeId, StoreLocationParLevelRequest req) {
        StoreLocationParLevel entity = repository
                .findByStoreIdAndLocationCodeAndProductId(storeId, req.locationCode(), req.productId())
                .orElseGet(() -> StoreLocationParLevel.builder()
                        .storeId(storeId)
                        .locationCode(req.locationCode())
                        .productId(req.productId())
                        .build());

        entity.setParQty(req.parQty());
        entity.setMinQty(req.minQty());
        entity.setActive(true);
        return StoreLocationParLevelResponse.from(repository.save(entity));
    }

    @SuppressWarnings("null")
    @Transactional
    public void delete(UUID id, UUID storeId) {
        StoreLocationParLevel entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StoreLocationParLevel", id));
        if (!entity.getStoreId().equals(storeId)) {
            throw new ResourceNotFoundException("StoreLocationParLevel", id);
        }
        entity.setActive(false);
        repository.save(entity);
    }
}
