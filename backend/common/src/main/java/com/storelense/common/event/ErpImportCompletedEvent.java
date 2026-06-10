package com.storelense.common.event;

import java.time.Instant;
import java.util.UUID;

public record ErpImportCompletedEvent(
        String  eventId,
        UUID    batchId,
        UUID    storeId,
        Instant importedAt,
        int     totalSnapshots,
        int     resolvedEpcCount,
        int     unresolvedEanCount,
        String  zoneRegion
) {}
