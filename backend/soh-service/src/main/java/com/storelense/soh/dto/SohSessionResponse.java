package com.storelense.soh.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SohSessionResponse(
        UUID            id,
        UUID            storeId,
        UUID            zoneId,
        String          sessionType,
        String          status,
        UUID            startedBy,
        OffsetDateTime  startedAt,
        OffsetDateTime  completedAt,
        OffsetDateTime  pausedAt,
        OffsetDateTime  resumedAt,
        OffsetDateTime  uploadedAt,
        OffsetDateTime  reconciledAt,
        OffsetDateTime  closedAt,
        int             totalEpcReads,
        int             uniqueEpcCount,
        String          notes,
        String          source,
        String          zoneRegion,
        UUID            cycleCountId,
        String          locationCode,
        String          sectionCode,
        List<String>    expectedEpcs,   // populated only when includeEpcs=true
        SohResultResponse result
) {}
