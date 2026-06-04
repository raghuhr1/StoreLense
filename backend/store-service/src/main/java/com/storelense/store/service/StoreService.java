package com.storelense.store.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.exception.BusinessException;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.store.domain.entity.Store;
import com.storelense.store.domain.entity.StoreConfig;
import com.storelense.store.domain.repository.StoreRepository;
import com.storelense.store.dto.*;
import com.storelense.store.mapper.StoreMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreMapper     storeMapper;

    @Transactional(readOnly = true)
    public PageResponse<StoreResponse> listStores(Pageable pageable) {
        return PageResponse.from(storeRepository.findByActiveTrue(pageable).map(storeMapper::toResponse));
    }

    @Transactional(readOnly = true)
    public StoreResponse getStore(UUID id) {
        return storeMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public StoreResponse createStore(CreateStoreRequest req) {
        if (storeRepository.existsByStoreCode(req.storeCode())) {
            throw new BusinessException("STORE_CODE_EXISTS", "Store code already in use", HttpStatus.CONFLICT);
        }
        Store store = storeMapper.toEntity(req);
        StoreConfig config = StoreConfig.builder().store(store).build();
        store.setConfig(config);
        return storeMapper.toResponse(storeRepository.save(store));
    }

    @Transactional
    public StoreResponse updateStore(UUID id, UpdateStoreRequest req) {
        Store store = findOrThrow(id);
        storeMapper.updateEntity(req, store);
        return storeMapper.toResponse(storeRepository.save(store));
    }

    @Transactional
    public void deactivateStore(UUID id) {
        Store store = findOrThrow(id);
        store.setActive(false);
        storeRepository.save(store);
    }

    private Store findOrThrow(UUID id) {
        return storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store", id));
    }
}
