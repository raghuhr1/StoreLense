package com.storelense.inventory.dto;

/**
 * One day of zone_scan_rollup history — counts of items per status on that day.
 * Used to chart how zone health evolves over time after each scan session.
 */
public record ZoneTrendPoint(
        String day,            // ISO date yyyy-MM-dd
        int    criticalCount,
        int    lowCount,
        int    okCount,
        int    surplusCount
) {}
