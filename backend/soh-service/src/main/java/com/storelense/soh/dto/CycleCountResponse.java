package com.storelense.soh.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CycleCountResponse(
        UUID            id,
        UUID            storeId,
        LocalDate       countDate,
        String          status,
        UUID            createdBy,
        String          notes,
        OffsetDateTime  createdAt,
        OffsetDateTime  updatedAt,
        List<SohSessionResponse> sessions   // populated on detail fetch; null on list
) {}
