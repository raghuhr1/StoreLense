package com.storelense.common.event;

import java.time.Instant;

public record ErpProductSyncEvent(
        String  eventId,
        String  erpProductCode,
        String  sku,
        String  name,
        String  categoryCode,
        String  brand,
        String  unitOfMeasure,
        Integer weightGrams,
        boolean rfidEnabled,
        boolean active,
        Instant syncedAt
) {}
