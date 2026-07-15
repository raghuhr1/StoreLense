package com.storelense.inventory.dto;

import java.util.UUID;

/**
 * A suggested refill task derived from the store's most recent completed SOH session's
 * Sales Floor count, compared against store_location_par_levels and matched to an active
 * replenishment rule. hasOpenTask=true means a pending/in-progress task already covers this
 * line (including tasks auto-created by the SOH-session-completed trigger in refill-service) —
 * the web can skip or grey-out these to avoid duplicates.
 *
 * Phase 3: replaces the previous Zone/live-antenna-scan-based suggestion source.
 */
public record ReplenishmentSuggestion(
        UUID   storeId,
        String locationCode, // always "SALES_FLOOR" for now
        UUID   productId,
        String sku,
        String productName,
        int    scannedQty,   // Sales Floor count from the latest completed SOH session
        int    parQty,
        int    shortage,     // abs(variance) — how many units short of par
        String status,       // critical | low
        short  priority,     // from matching rule (1-10)
        boolean hasOpenTask  // deduplication flag — open refill task already exists
) {}
