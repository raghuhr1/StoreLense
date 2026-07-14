package com.storelense.inventory.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BillLookupResponse(
        UUID            id,
        String          billRef,
        UUID            storeId,
        OffsetDateTime  createdAt,
        List<BillItemDto> items,
        String          status,
        OffsetDateTime  gateCheckedAt
) {}
