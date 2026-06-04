package com.storelense.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SohResultOutboundEvent(
        String       eventId,
        UUID         sessionId,
        UUID         storeId,
        String       erpStoreCode,
        BigDecimal   accuracyPct,
        int          totalProductsCounted,
        int          totalUnitsCounted,
        int          totalUnitsExpected,
        List<VarianceItem> variances,
        Instant      completedAt
) {
    public record VarianceItem(
            String erpProductCode,
            String sku,
            int    countedQty,
            int    expectedQty,
            int    varianceQty
    ) {}
}
