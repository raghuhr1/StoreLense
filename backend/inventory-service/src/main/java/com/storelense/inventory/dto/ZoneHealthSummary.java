package com.storelense.inventory.dto;

import java.util.UUID;

/**
 * Live per-zone stock health — product counts by rollup status.
 * Derived from current epc_registry vs zone_par_levels (no snapshot needed).
 */
public record ZoneHealthSummary(
        UUID   zoneId,
        String zoneName,
        int    criticalCount,
        int    lowCount,
        int    okCount,
        int    surplusCount,
        int    totalProducts
) {}
