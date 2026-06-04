package com.storelense.soh.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SohResultResponse(
        UUID id, UUID sessionId, UUID storeId,
        int totalProductsCounted, int totalUnitsCounted, int totalUnitsExpected,
        BigDecimal accuracyPct, int varianceCount, int overcountItems, int undercountItems,
        OffsetDateTime resultGeneratedAt
) {}
