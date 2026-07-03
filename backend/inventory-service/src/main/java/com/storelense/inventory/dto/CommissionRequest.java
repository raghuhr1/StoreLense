package com.storelense.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CommissionRequest(
        @NotNull UUID storeId,
        @NotBlank String sku,
        @NotBlank @Pattern(regexp = "[0-9A-Fa-f]{16,64}", message = "EPC must be 16–64 hex characters")
        String epc,
        @NotBlank String zone
) {}
