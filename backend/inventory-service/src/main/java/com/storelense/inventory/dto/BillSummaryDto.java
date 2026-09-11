package com.storelense.inventory.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row in the bill list. Deliberately omits line items - the list view only
 * needs headline info; callers wanting items fetch GET /bills/{billRef}.
 */
public record BillSummaryDto(
        UUID            id,
        String          billRef,
        UUID            storeId,
        UUID            cashierId,
        int             totalItems,
        BigDecimal      totalValue,
        String          status,
        OffsetDateTime  createdAt,
        OffsetDateTime  gateCheckedAt
) {}
