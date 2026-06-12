package com.storelense.inventory.dto;

import com.storelense.inventory.domain.entity.InboundShipment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InboundShipmentResponse(
        UUID id,
        UUID storeId,
        String dcCode,
        String referenceNumber,
        String status,
        OffsetDateTime expectedAt,
        OffsetDateTime receivedAt,
        int lineCount,
        String notes,
        OffsetDateTime createdAt
) {
    public static InboundShipmentResponse from(InboundShipment s) {
        return new InboundShipmentResponse(
                s.getId(), s.getStoreId(), s.getDcCode(), s.getReferenceNumber(),
                s.getStatus(), s.getExpectedAt(), s.getReceivedAt(),
                s.getLineCount(), s.getNotes(), s.getCreatedAt()
        );
    }
}
