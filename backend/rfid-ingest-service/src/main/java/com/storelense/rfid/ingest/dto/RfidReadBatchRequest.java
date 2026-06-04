package com.storelense.rfid.ingest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record RfidReadBatchRequest(
        @NotNull UUID rfidSessionId,
        @NotNull UUID storeId,
        UUID readerId,
        String deviceId,
        @NotEmpty @Valid List<RfidReadEntry> reads
) {}
