package com.storelense.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ZoneParLevelRequest(
        @NotNull UUID storeId,
        @NotNull UUID zoneId,
        @NotNull UUID productId,
        @Min(0) int parQty,
        @Min(0) int minQty
) {}
