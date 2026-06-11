package com.storelense.inventory.dto;

import java.util.UUID;

public record SkuLedgerRow(
        UUID   productId,
        int    inStore,
        int    sold,
        int    missing,
        int    damaged,
        int    transferred,
        int    total,
        String lastSeenAt
) {}
