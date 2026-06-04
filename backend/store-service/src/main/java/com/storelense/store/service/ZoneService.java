package com.storelense.store.service;

import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.store.domain.entity.Store;
import com.storelense.store.domain.entity.Zone;
import com.storelense.store.domain.repository.StoreRepository;
import com.storelense.store.domain.repository.ZoneRepository;
import com.storelense.store.dto.CreateZoneRequest;
import com.storelense.store.dto.UpdateZoneRequest;
import com.storelense.store.dto.ZoneResponse;
import com.storelense.store.mapper.ZoneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository  zoneRepository;
    private final StoreRepository storeRepository;
    private final ZoneMapper      zoneMapper;

    @Transactional(readOnly = true)
    public List<ZoneResponse> listZones(UUID storeId) {
        return zoneRepository.findByStore_IdAndActiveTrueOrderByDisplayOrderAsc(storeId)
                .stream().map(zoneMapper::toResponse).toList();
    }

    @Transactional
    public ZoneResponse createZone(UUID storeId, CreateZoneRequest req) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store", storeId));

        if (zoneRepository.existsByStore_IdAndZoneCode(storeId, req.zoneCode())) {
            throw new BusinessException("ZONE_CODE_EXISTS",
                    "Zone code '" + req.zoneCode() + "' already exists in this store", HttpStatus.CONFLICT);
        }

        Zone zone = Zone.builder()
                .store(store)
                .zoneCode(req.zoneCode())
                .name(req.name())
                .zoneType(req.zoneType() != null ? req.zoneType() : "floor")
                .displayOrder(req.displayOrder() != null ? req.displayOrder() : 0)
                .build();

        return zoneMapper.toResponse(zoneRepository.save(zone));
    }

    @Transactional
    public ZoneResponse updateZone(UUID storeId, UUID zoneId, UpdateZoneRequest req) {
        Zone zone = zoneRepository.findById(zoneId)
                .filter(z -> z.getStore().getId().equals(storeId))
                .orElseThrow(() -> new ResourceNotFoundException("Zone", zoneId));

        if (req.name() != null)         zone.setName(req.name());
        if (req.zoneType() != null)     zone.setZoneType(req.zoneType());
        if (req.displayOrder() != null) zone.setDisplayOrder(req.displayOrder());
        if (req.active() != null)       zone.setActive(req.active());

        return zoneMapper.toResponse(zoneRepository.save(zone));
    }
}
