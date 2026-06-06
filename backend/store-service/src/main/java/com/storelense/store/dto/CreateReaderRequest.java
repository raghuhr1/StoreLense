package com.storelense.store.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateReaderRequest(
        @NotBlank String readerCode,
        @NotBlank @Pattern(regexp = "fixed|handheld|bluetooth_sled") String readerType,
        UUID zoneId,
        String ipAddress,
        String firmwareVersion,
        @Min(1) @Max(32) short antennaCount,
        BigDecimal txPowerDbm
) {
    public CreateReaderRequest {
        if (antennaCount == 0) antennaCount = 4;
    }
}
