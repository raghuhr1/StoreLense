package com.storelense.soh.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ParticipantResponse(
        UUID id,
        String deviceId,
        String zoneRegion,
        String status,
        OffsetDateTime joinedAt,
        OffsetDateTime completedAt
) {}
