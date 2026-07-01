package com.storelense.inventory.dto;

import java.util.UUID;

public record CommissionResponse(
        String epc,
        String sku,
        String productName,
        UUID   productId,
        UUID   storeId,
        String zone,
        int    totalTaggedInStore
) {}
