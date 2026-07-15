package com.storelense.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

public record StoreLocationParLevelRequest(
        @NotNull UUID storeId,
        @NotNull @Pattern(regexp = "SALES_FLOOR|BACKROOM") String locationCode,
        @NotNull UUID productId,
        @Min(0) int parQty,
        @Min(0) int minQty
) {}
