package com.storelense.auth.dto;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID            id,
        String          username,
        String          email,
        String          firstName,
        String          lastName,
        UUID            storeId,
        boolean         active,
        Set<String>     roles,
        OffsetDateTime  lastLoginAt,
        OffsetDateTime  createdAt
) {}
