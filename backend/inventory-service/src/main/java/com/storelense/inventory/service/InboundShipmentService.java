package com.storelense.inventory.service;

import com.storelense.common.dto.PageResponse;
import com.storelense.common.exception.ResourceNotFoundException;
import com.storelense.inventory.domain.entity.EpcPositionHistory;
import com.storelense.inventory.domain.entity.EpcRegistry;
import com.storelense.inventory.domain.entity.InboundShipment;
import com.storelense.inventory.domain.repository.EpcPositionHistoryRepository;
import com.storelense.inventory.domain.repository.EpcRegistryRepository;
import com.storelense.inventory.domain.repository.InboundShipmentRepository;
import com.storelense.inventory.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InboundShipmentService {

    private final InboundShipmentRepository   repository;
    private final EpcRegistryRepository       epcRegistryRepository;
    private final EpcPositionHistoryRepository epcPositionHistoryRepository;

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

    @SuppressWarnings("null")
    @Transactional
    public ReceiveShipmentResponse receiveShipment(UUID id, ReceiveShipmentRequest req) {
        InboundShipment shipment = findOrThrow(id);
        shipment.setStatus("received");
        shipment.setReceivedAt(OffsetDateTime.now());
        repository.save(shipment);

        // Mark each scanned EPC as inbound in the registry and write position history.
        List<String> epcs = req.epcs() != null ? req.epcs() : List.of();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (String epc : epcs) {
            epcRegistryRepository.findByEpcAndStoreId(epc, shipment.getStoreId())
                    .ifPresentOrElse(
                            existing -> {
                                epcPositionHistoryRepository.save(EpcPositionHistory.builder()
                                        .epc(epc)
                                        .storeId(shipment.getStoreId())
                                        .productId(existing.getProductId())
                                        .fromZoneId(existing.getZoneId())
                                        .toZoneId(null)
                                        .fromStatus(existing.getStatus())
                                        .toStatus("inbound")
                                        .triggeredBy("receiving")
                                        .sessionId(id)
                                        .build());
                                existing.setStatus("inbound");
                                existing.setZoneId(null);
                                existing.setLastSeenAt(now);
                                epcRegistryRepository.save(existing);
                            },
                            () -> {
                                epcRegistryRepository.save(EpcRegistry.builder()
                                        .epc(epc)
                                        .storeId(shipment.getStoreId())
                                        .status("inbound")
                                        .lastSeenAt(now)
                                        .build());
                                epcPositionHistoryRepository.save(EpcPositionHistory.builder()
                                        .epc(epc)
                                        .storeId(shipment.getStoreId())
                                        .fromZoneId(null)
                                        .toZoneId(null)
                                        .fromStatus(null)
                                        .toStatus("inbound")
                                        .triggeredBy("receiving")
                                        .sessionId(id)
                                        .build());
                            }
                    );
        }

        return new ReceiveShipmentResponse(id, epcs.size(), "received");
    }

    private InboundShipment findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InboundShipment", id));
    }
}
