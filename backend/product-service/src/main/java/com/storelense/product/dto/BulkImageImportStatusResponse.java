package com.storelense.product.dto;

import java.util.List;
import java.util.UUID;

public record BulkImageImportStatusResponse(
        UUID   jobId,
        String state,
        int    totalRows,
        int    processed,
        int    imported,
        int    skippedNoProduct,
        int    failed,
        List<String> errors
) {}
