package com.storelense.inventory.dto;

import java.util.UUID;

/**
 * A suggested refill task derived from a rollup row that matches an active rule.
 * hasOpenTask=true means a pending/in-progress task already covers this line —
 * the web can skip or grey-out these to avoid duplicates.
 */
public record ReplenishmentSuggestion(
        UUID   storeId,
        UUID   zoneId,
        String zoneName,
        UUID   productId,
        String sku,
        String productName,
        int    scannedQty,
        int    parQty,
        int    shortage,     // abs(variance) — how many units short of par
        String status,       // critical | low
        short  priority,     // from matching rule (1-10)
        boolean hasOpenTask  // deduplication flag — open refill task already exists
) {}
