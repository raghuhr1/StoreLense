package com.storelense.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Non-RFID bill items verified by barcode at the gate — no EPC exists, so sale is
 *  recorded by EAN + quantity instead of by tag. */
public record MarkNonRfidSoldRequest(
        @NotNull UUID storeId,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotBlank String ean,
            @Min(1) int qty
    ) {}
}
