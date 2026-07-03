package com.storelense.soh.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SohResultResponse(
        UUID           id,
        UUID           sessionId,
        UUID           storeId,
        // store-level totals
        int            totalProductsCounted,
        int            totalUnitsCounted,
        int            totalUnitsExpected,
        BigDecimal     accuracyPct,
        int            varianceCount,
        int            overcountItems,
        int            undercountItems,
        // sales floor breakdown
        int            floorUnitsCounted,
        int            floorUnitsExpected,
        int            floorVariance,
        // backroom breakdown
        int            backroomUnitsCounted,
        int            backroomUnitsExpected,
        int            backroomVariance,
        // combined store variance
        int            totalStoreVariance,
        OffsetDateTime resultGeneratedAt
) {}
