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
        @NotBlank String zone,
        // Only set when the associate is explicitly retagging a specific broken/lost
        // tag. Left null for the normal case of tagging a genuinely new physical unit.
        @Pattern(regexp = "[0-9A-Fa-f]{16,64}", message = "EPC must be 16–64 hex characters")
        String replacesEpc
) {}
