package com.storelense.inventory.service;

import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.inventory.domain.entity.ZoneParLevel;
import com.storelense.inventory.domain.repository.ZoneParLevelRepository;
import com.storelense.inventory.dto.ZoneParLevelRequest;
import com.storelense.inventory.dto.ZoneParLevelResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZoneParLevelService {

    private final ZoneParLevelRepository repository;

    @Transactional(readOnly = true)
    public List<ZoneParLevelResponse> list(UUID storeId, UUID zoneId) {
        List<ZoneParLevel> rows = (zoneId != null)
                ? repository.findByStoreIdAndZoneIdAndActiveTrue(storeId, zoneId)
                : repository.findByStoreIdAndActiveTrue(storeId);
        return rows.stream().map(ZoneParLevelResponse::from).toList();
    }

    @SuppressWarnings("null")
    @Transactional
    public ZoneParLevelResponse upsert(UUID storeId, ZoneParLevelRequest req) {
        ZoneParLevel entity = repository
                .findByStoreIdAndZoneIdAndProductId(storeId, req.zoneId(), req.productId())
                .orElseGet(() -> ZoneParLevel.builder()
                        .storeId(storeId)
                        .zoneId(req.zoneId())
                        .productId(req.productId())
                        .build());

        entity.setParQty(req.parQty());
        entity.setMinQty(req.minQty());
        entity.setActive(true);
        return ZoneParLevelResponse.from(repository.save(entity));
    }

    @SuppressWarnings("null")
    @Transactional
    public void delete(UUID id, UUID storeId) {
        ZoneParLevel entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ZoneParLevel", id));
        if (!entity.getStoreId().equals(storeId)) {
            throw new ResourceNotFoundException("ZoneParLevel", id);
        }
        entity.setActive(false);
        repository.save(entity);
    }
}
