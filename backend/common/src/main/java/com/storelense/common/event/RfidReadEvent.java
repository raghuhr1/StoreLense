package com.storelense.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RfidReadEvent(
        String  eventId,
        UUID    rfidSessionId,
        UUID    storeId,
        UUID    readerId,
        String  epc,
        BigDecimal rssi,
        Integer antennaPort,
        Instant readAt,
        String  correlationId
) {}
