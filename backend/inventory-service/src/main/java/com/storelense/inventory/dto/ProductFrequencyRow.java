package com.storelense.inventory.dto;

import java.util.UUID;

/**
 * Top products by auto-triggered refill task frequency.
 * Products that appear frequently here are chronic under-stockers — candidates
 * for a higher par level or faster replenishment cycle.
 */
public record ProductFrequencyRow(
        UUID   productId,
        String sku,
        String productName,
        int    refillCount,
        int    totalUnitsRequested,
        String lastRefillAt
) {}
