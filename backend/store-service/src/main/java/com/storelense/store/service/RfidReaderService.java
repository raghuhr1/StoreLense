package com.storelense.store.service;

import com.storelense.common.exception.BusinessException;
import com.storelense.store.domain.entity.RfidReader;
import com.storelense.store.domain.repository.RfidReaderRepository;
import com.storelense.store.domain.repository.StoreRepository;
import com.storelense.store.dto.CreateReaderRequest;
import com.storelense.store.dto.RfidReaderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RfidReaderService {

    private final RfidReaderRepository readerRepository;
    private final StoreRepository      storeRepository;

    @Transactional(readOnly = true)
    public List<RfidReaderResponse> listReaders(UUID storeId) {
        return readerRepository.findByStoreIdAndActiveTrue(storeId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public RfidReaderResponse createReader(UUID storeId, CreateReaderRequest req) {
        if (!storeRepository.existsById(storeId)) {
            throw new BusinessException("STORE_NOT_FOUND", "Store not found", HttpStatus.NOT_FOUND);
        }
        RfidReader reader = RfidReader.builder()
                .storeId(storeId)
                .zoneId(req.zoneId())
                .readerCode(req.readerCode())
                .readerType(req.readerType())
                .ipAddress(req.ipAddress())
                .firmwareVersion(req.firmwareVersion())
                .antennaCount(req.antennaCount())
                .txPowerDbm(req.txPowerDbm())
                .build();
        return toResponse(readerRepository.save(reader));
    }

    private RfidReaderResponse toResponse(RfidReader r) {
        return new RfidReaderResponse(
                r.getId(), r.getStoreId(), r.getZoneId(),
                r.getReaderCode(), r.getReaderType(), r.getIpAddress(),
                r.getFirmwareVersion(), r.getAntennaCount(), r.getTxPowerDbm(),
                r.isActive(), r.getLastHeartbeatAt()
        );
    }
}
