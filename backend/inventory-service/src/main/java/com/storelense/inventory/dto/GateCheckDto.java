package com.storelense.inventory.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record GateCheckDto(
        UUID            id,
        UUID            storeId,
        String          billRef,
        OffsetDateTime  checkedAt,
        int             expectedCount,
        int             matchedCount,
        int             extraCount,
        String          outcome,
        List<String>    epcsMatched,
        List<String>    epcsExtra
) {}
