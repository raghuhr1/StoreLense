package com.storelense.inventory.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.inventory.domain.entity.InboundShipment;
import com.storelense.inventory.domain.repository.InboundShipmentRepository;
import com.storelense.inventory.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboundShipmentService {

    private final InboundShipmentRepository repository;

    @Transactional(readOnly = true)
    public PageResponse<InboundShipmentResponse> listShipments(UUID storeId, String status, Pageable pageable) {
        var page = (status != null && !status.isBlank())
                ? repository.findByStoreIdAndStatusInOrderByExpectedAtDesc(
                        storeId, List.of(status.split(",")), pageable)
                : repository.findByStoreIdOrderByExpectedAtDesc(storeId, pageable);
        return PageResponse.from(page.map(InboundShipmentResponse::from));
    }

    @Transactional(readOnly = true)
    public InboundShipmentResponse getShipment(UUID id) {
        return InboundShipmentResponse.from(findOrThrow(id));
    }

    @Transactional
    public InboundShipmentResponse createShipment(CreateShipmentRequest req) {
        InboundShipment shipment = InboundShipment.builder()
                .storeId(req.storeId())
                .dcCode(req.dcCode())
                .referenceNumber(req.referenceNumber())
                .status("expected")
                .expectedAt(req.expectedAt())
                .lineCount(req.lineCount())
                .notes(req.notes())
                .build();
        return InboundShipmentResponse.from(repository.save(shipment));
    }

    @Transactional
    public ReceiveShipmentResponse receiveShipment(UUID id, ReceiveShipmentRequest req) {
        InboundShipment shipment = findOrThrow(id);
        shipment.setStatus("received");
        shipment.setReceivedAt(OffsetDateTime.now());
        repository.save(shipment);
        return new ReceiveShipmentResponse(id, req.epcs() != null ? req.epcs().size() : 0, "received");
    }

    private InboundShipment findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InboundShipment", id));
    }
}
