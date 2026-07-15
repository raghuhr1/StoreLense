package com.storelense.auth.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RoleResponse(
        UUID           id,
        String         name,
        String         description,
        long           userCount,
        OffsetDateTime createdAt
) {}
