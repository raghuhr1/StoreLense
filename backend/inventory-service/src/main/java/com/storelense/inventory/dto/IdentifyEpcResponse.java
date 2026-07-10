package com.storelense.inventory.dto;

import java.util.List;
import java.util.UUID;

public record IdentifyEpcResponse(
        String      epc,
        UUID        productId,
        String      sku,
        String      productName,
        List<String> eans,
        String      statusInStore,
        String      zoneName,
        boolean     alreadyRegistered
) {}
