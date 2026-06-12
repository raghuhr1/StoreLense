package com.storelense.inventory.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateShipmentRequest(
        UUID storeId,
        String dcCode,
        String referenceNumber,
        OffsetDateTime expectedAt,
        int lineCount,
        String notes
) {}
