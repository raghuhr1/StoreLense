package com.storelense.soh.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartSessionRequest(
        @NotNull UUID storeId,
        UUID zoneId,
        String sessionType,
        String notes,
        String source,
        String zoneRegion
) {}
