package com.storelense.store.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RfidReaderResponse(
        UUID id,
        UUID storeId,
        UUID zoneId,
        String readerCode,
        String readerType,
        String ipAddress,
        String firmwareVersion,
        short antennaCount,
        BigDecimal txPowerDbm,
        boolean active,
        OffsetDateTime lastHeartbeatAt
) {}
