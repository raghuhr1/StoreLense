package com.storelense.common.event;

import java.time.Instant;
import java.util.UUID;

public record ErpInventoryExpectedEvent(
        String  eventId,
        UUID    storeId,
        String  erpStoreCode,
        String  erpProductCode,
        String  sku,
        int     expectedQuantity,
        Instant validAt
) {}
