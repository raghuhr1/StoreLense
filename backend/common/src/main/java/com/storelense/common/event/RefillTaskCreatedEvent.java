package com.storelense.common.event;

import java.time.Instant;
import java.util.UUID;

public record RefillTaskCreatedEvent(
        String  eventId,
        UUID    taskId,
        UUID    storeId,
        String  source,
        UUID    sourceSessionId,
        Instant createdAt
) {}
