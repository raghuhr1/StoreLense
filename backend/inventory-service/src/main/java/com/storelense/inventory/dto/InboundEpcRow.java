package com.storelense.inventory.dto;

import java.util.UUID;

public record InboundEpcRow(
        String epc,
        UUID   productId,
        String sku,
        String productName,
        String firstSeenAt,
        String lastSeenAt
) {}
