package com.storelense.product.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id, String sku, String name, String description,
        UUID categoryId, String brand, String supplierCode,
        String erpProductCode, String unitOfMeasure,
        Integer weightGrams, boolean rfidEnabled, boolean active,
        String primaryEan,
        OffsetDateTime erpSyncedAt, OffsetDateTime createdAt
) {}
