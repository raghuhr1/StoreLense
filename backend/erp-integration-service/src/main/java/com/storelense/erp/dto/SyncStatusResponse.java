package com.storelense.erp.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SyncStatusResponse(
        UUID           id,
        String         syncType,
        String         direction,
        String         status,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        int            recordsFetched,
        int            recordsPublished,
        int            recordsFailed,
        String         errorMessage,
        String         triggeredBy
) {}
