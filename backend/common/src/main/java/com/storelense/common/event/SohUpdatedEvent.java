package com.storelense.common.event;

import java.time.Instant;
import java.util.UUID;

public record SohUpdatedEvent(
        String  eventId,
        UUID    rfidSessionId,
        UUID    sohSessionId,
        UUID    storeId,
        UUID    productId,
        UUID    zoneId,
        String  epc,
        Instant processedAt,
        String  locationCode,
        String  sectionCode
) {}
