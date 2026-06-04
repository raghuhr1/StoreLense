package com.storelense.soh.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SohSessionResponse(
        UUID id, UUID storeId, UUID zoneId,
        String sessionType, String status,
        UUID startedBy, OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        int totalEpcReads, int uniqueEpcCount,
        String notes
) {}
