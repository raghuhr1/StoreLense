package com.storelense.inventory.dto;

import java.util.UUID;

/**
 * Enriched rollup row — zone + product names resolved from cross-schema joins.
 * Returned by both the live view and the persisted-session view.
 */
public record ZoneScanRollupRow(
        UUID   id,
        UUID   storeId,
        UUID   zoneId,
        String zoneName,
        UUID   productId,
        String sku,
        String productName,
        UUID   sessionId,
        int    scannedQty,
        int    parQty,
        int    minQty,
        int    variance,
        String status,       // ok | low | critical | surplus
        String computedAt
) {}
