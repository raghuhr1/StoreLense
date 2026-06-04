package com.storelense.rfid.ingest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

public record RfidReadEntry(
        @NotBlank @Pattern(regexp = "[0-9A-Fa-f]{16,128}") String epc,
        BigDecimal rssi,
        Integer antennaPort,
        Instant readAt
) {}
