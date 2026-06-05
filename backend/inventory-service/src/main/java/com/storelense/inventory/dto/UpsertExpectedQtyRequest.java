package com.storelense.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for POST /api/inventory/expected
 * Used by the XLS upload tool to set ERP expected quantities
 * so RFID scan data can be compared against them.
 */
public record UpsertExpectedQtyRequest(

        @NotNull UUID    storeId,
        @NotNull UUID    productId,

        /** Zone scope — null means store-level (no zone breakdown). */
        UUID     zoneId,

        @NotNull @Min(0) Integer quantityExpected,

        /** Informational — date the ERP data is valid for. */
        LocalDate validDate,

        /** Free-text notes (e.g. "xls_import", "post-receiving adjustment"). */
        String notes,

        /** Import source identifier (e.g. "xls_import", "erp_api"). */
        String source
) {}
