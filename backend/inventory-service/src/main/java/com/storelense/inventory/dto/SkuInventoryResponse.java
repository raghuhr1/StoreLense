package com.storelense.inventory.dto;

import java.util.List;
import java.util.UUID;

public record SkuInventoryResponse(
        String       sku,
        UUID         productId,
        UUID         storeId,
        int          onFloor,
        int          inBackroom,
        int          total,
        List<String> epcs
) {}
