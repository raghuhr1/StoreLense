package com.storelense.inventory.dto;

import java.util.UUID;

public record EpcLedgerRow(
        String epc,
        UUID   productId,
        String sku,
        String productName,
        String zoneName,
        String status,
        String lastSeenAt,
        String firstSeenAt
) {}
